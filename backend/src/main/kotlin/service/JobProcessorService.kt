package me.pavekovt.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.ConflictFeelingsDTO
import me.pavekovt.entity.*
import me.pavekovt.repository.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Background job processor that handles async AI operations using Kotlin Channels.
 * Publishes results via SharedFlow for SSE streaming to clients.
 */
class JobProcessorService(
    private val jobRepository: JobRepository,
    private val conflictFeelingsRepository: ConflictFeelingsRepository,
    private val conflictRepository: ConflictRepository,
    private val resolutionRepository: ResolutionRepository,
    private val aiSummaryRepository: AISummaryRepository,
    private val retrospectiveRepository: RetrospectiveRepository,
    private val userRepository: UserRepository,
    private val partnershipRepository: PartnershipRepository,
    private val partnershipContextRepository: PartnershipContextRepository,
    private val aiProvider: AIProvider,
    private val journalContextProcessor: JournalContextProcessor
) {
    private val logger = LoggerFactory.getLogger(JobProcessorService::class.java)
    private val jobChannel = Channel<UUID>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // SSE event streams for real-time updates
    private val _eventFlow = MutableSharedFlow<JobEvent>(replay = 0, extraBufferCapacity = 100)
    val eventFlow: SharedFlow<JobEvent> = _eventFlow.asSharedFlow()

    init {
        logger.info("JobProcessorService init block starting...")
        // Start worker coroutines
        repeat(3) { workerId ->
            scope.launch {
                logger.info("Starting job worker $workerId")
                processJobs(workerId)
            }
        }

        // Start job poller
        scope.launch {
            logger.info("Starting job poller")
            pollPendingJobs()
        }
        logger.info("JobProcessorService init block completed - workers started")
    }

    /**
     * Queue a new job for processing
     */
    suspend fun queueJob(jobId: UUID) {
        logger.info("Queueing job $jobId")
        jobChannel.send(jobId)
        logger.info("Job $jobId queued successfully")
    }

    /**
     * Worker coroutine that processes jobs from the channel
     */
    private suspend fun processJobs(workerId: Int) {
        logger.info("Worker $workerId waiting for jobs...")
        for (jobId in jobChannel) {
            try {
                logger.info("Worker $workerId picked up job $jobId")
                processJob(jobId)
                logger.info("Worker $workerId completed job $jobId")
            } catch (e: Exception) {
                logger.error("Worker $workerId failed to process job $jobId", e)
            }
        }
        logger.info("Worker $workerId shutting down")
    }

    /**
     * Poll database for pending jobs (in case jobs were created while server was down)
     */
    private suspend fun pollPendingJobs() {
        while (true) {
            try {
                val pendingJobs = jobRepository.findPendingJobs(limit = 10)
                pendingJobs.forEach { job ->
                    queueJob(UUID.fromString(job.id))
                }
                delay(5000) // Poll every 5 seconds
            } catch (e: Exception) {
                logger.error("Error polling pending jobs", e)
                delay(10000) // Back off on error
            }
        }
    }

    /**
     * Process a single job based on its type
     */
    private suspend fun processJob(jobId: UUID) {
        val job = jobRepository.findById(jobId)
        if (job == null) {
            logger.error("Job $jobId not found")
            return
        }

        // Skip if already processing or completed
        if (job.status != JobStatus.PENDING) {
            logger.debug("Job {} already processed (status: {})", jobId, job.status)
            return
        }

        try {
            jobRepository.markAsStarted(jobId)
            _eventFlow.emit(JobEvent.Started(jobId.toString(), job.jobType, job.entityId))

            when (job.jobType) {
                JobType.PROCESS_FEELINGS -> processFeelingsJob(jobId, UUID.fromString(job.entityId), job.payload)
                JobType.GENERATE_SUMMARY -> generateSummaryJob(jobId, UUID.fromString(job.entityId))
                JobType.GENERATE_DISCUSSION_POINTS -> generateDiscussionPointsJob(jobId, UUID.fromString(job.entityId))
                JobType.UPDATE_PARTNERSHIP_CONTEXT -> updatePartnershipContextJob(jobId, UUID.fromString(job.entityId), job.payload)
            }

            jobRepository.markAsCompleted(jobId)
            _eventFlow.emit(JobEvent.Completed(jobId.toString(), job.jobType, job.entityId))

        } catch (e: Exception) {
            logger.error("Job $jobId failed with exception: ${e.javaClass.name}: ${e.message}", e)
            logger.error("Full stack trace:", e)
            val errorMessage = e.message ?: "Unknown error"

            // Retry logic
            if (job.retryCount < 3) {
                logger.info("Retrying job $jobId (attempt ${job.retryCount + 1}/3)")
                jobRepository.incrementRetryCount(jobId)
                jobRepository.updateStatus(jobId, JobStatus.PENDING) // Re-queue
                queueJob(jobId)
                _eventFlow.emit(JobEvent.Retrying(jobId.toString(), job.jobType, job.entityId, job.retryCount + 1))
            } else {
                logger.error("Job $jobId exceeded max retries, marking as failed")
                jobRepository.markAsFailed(jobId, errorMessage)
                _eventFlow.emit(JobEvent.Failed(jobId.toString(), job.jobType, job.entityId, errorMessage))
            }
        }
    }

    /**
     * Process feelings job: Generate AI guidance for user's feelings
     */
    private suspend fun processFeelingsJob(jobId: UUID, feelingId: UUID, payload: String?) {
        logger.info("Processing feelings job for feeling $feelingId")

        // Get the feeling entry
        val feeling = conflictFeelingsRepository.findById(feelingId)
            ?: throw IllegalStateException("Feeling $feelingId not found")

        // Get user ID for context
        val feelingUserId = UUID.fromString(feeling.userId)

        // Get conflict for context
        val conflict = conflictRepository.findById(UUID.fromString(feeling.conflictId), feelingUserId)
            ?: throw IllegalStateException("Conflict ${feeling.conflictId} not found")

        // Get all previous feelings from this user for this conflict (for context)
        val conflictId = UUID.fromString(conflict.id)
        val previousFeelings = conflictFeelingsRepository.findByConflictAndUser(
            conflictId,
            feelingUserId
        ).filter { it.status == ConflictFeelingsStatus.COMPLETED } // Only include completed ones

        // Build context from previous feelings
        val contextText = if (previousFeelings.isNotEmpty()) {
            """
            Previous feelings and concerns from this user about this conflict:
            ${previousFeelings.joinToString("\n\n") { "- ${it.feelingsText}" }}

            Current feeling being processed:
            """.trimIndent()
        } else {
            "This is the first feeling submitted by this user for this conflict."
        }

        // Extract partnership context if available from payload
        val partnershipContext = payload?.let {
            try {
                Json.parseToJsonElement(it).jsonObject["partnershipContext"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }
        }

        // Detect language from user input
        val detectedLanguage = aiProvider.detectLanguage(feeling.feelingsText)

        // Get user profiles for AI context
        val userId = UUID.fromString(feeling.userId)
        val user = userRepository.findById(userId)
            ?: throw IllegalStateException("User $userId not found")

        // Get partner from conflict
        val initiatorId = UUID.fromString(conflict.initiatedBy)
        val partnerId = if (initiatorId == userId) {
            // Find partner from partnership
            val partnership = partnershipRepository.findActivePartnership(userId)
            UUID.fromString(partnership?.partnerId ?: throw IllegalStateException("No active partnership"))
        } else {
            initiatorId
        }

        val partner = userRepository.findById(partnerId)
            ?: throw IllegalStateException("Partner $partnerId not found")

        val userProfile = me.pavekovt.ai.UserProfile(
            name = user.name,
            age = user.age,
            gender = user.gender,
            description = user.description
        )

        val partnerProfile = me.pavekovt.ai.UserProfile(
            name = partner.name,
            age = partner.age,
            gender = partner.gender,
            description = partner.description
        )

        // Process unprocessed journals to update partnership context before AI call
        val partnership = partnershipRepository.findActivePartnership(userId)
        if (partnership != null) {
            val partnershipId = UUID.fromString(partnership.id)
            journalContextProcessor.processUnprocessedJournals(partnershipId, userId)
        }

        // Call AI with full context
        val aiResponse = aiProvider.processFeelingsAndSuggestResolution(
            userFeelings = feeling.feelingsText,
            userProfile = userProfile,
            partnerProfile = partnerProfile,
            partnershipContext = partnershipContext,
            previousFeelings = previousFeelings.map { it.feelingsText },
            detectedLanguage = detectedLanguage
        )

        // Update feeling with AI response
        conflictFeelingsRepository.updateWithAIResponse(
            feelingId = feelingId,
            aiGuidance = aiResponse.guidance,
            suggestedResolution = aiResponse.suggestedResolution,
            emotionalTone = aiResponse.emotionalTone
        )

        if (conflictFeelingsRepository.countCompletedFeelings(conflictId) >= 2) {
            conflictRepository.updateStatus(conflictId, ConflictStatus.PENDING_RESOLUTIONS)
        }

        logger.info("Completed feelings processing for feeling $feelingId")
    }

    /**
     * Generate summary job: Create AI summary from both resolutions
     */
    private suspend fun generateSummaryJob(jobId: UUID, conflictId: UUID) {
        logger.info("Generating summary for conflict $conflictId")

        // Get both resolutions
        val resolutions = resolutionRepository.findByConflict(conflictId)
        if (resolutions.size < 2) {
            throw IllegalStateException("Cannot generate summary: only ${resolutions.size} resolution(s) found")
        }

        val resolution1 = resolutions[0]
        val resolution2 = resolutions[1]

        // Get user profiles
        val user1Id = UUID.fromString(resolution1.userId)
        val user2Id = UUID.fromString(resolution2.userId)

        val user1 = userRepository.findById(user1Id)
            ?: throw IllegalStateException("User $user1Id not found")
        val user2 = userRepository.findById(user2Id)
            ?: throw IllegalStateException("User $user2Id not found")

        val user1Profile = me.pavekovt.ai.UserProfile(
            name = user1.name,
            age = user1.age,
            gender = user1.gender,
            description = user1.description
        )

        val user2Profile = me.pavekovt.ai.UserProfile(
            name = user2.name,
            age = user2.age,
            gender = user2.gender,
            description = user2.description
        )

        // Get partnership context
        val partnership = partnershipRepository.findActivePartnership(user1Id)
        val partnershipContext = if (partnership != null) {
            val partnershipId = UUID.fromString(partnership.id)

            // Process unprocessed journals to update partnership context before AI call
            journalContextProcessor.processUnprocessedJournals(partnershipId, user1Id)

            partnershipContextRepository.getContext(partnershipId)?.compactedSummary
        } else null

        // Detect language from resolutions
        val detectedLanguage = aiProvider.detectLanguage(resolution1.resolutionText + " " + resolution2.resolutionText)

        // Call AI to generate summary
        val summaryResult = aiProvider.summarizeConflict(
            resolution1 = resolution1.resolutionText,
            resolution2 = resolution2.resolutionText,
            user1Profile = user1Profile,
            user2Profile = user2Profile,
            partnershipContext = partnershipContext,
            detectedLanguage = detectedLanguage
        )

        // Save AI summary
        aiSummaryRepository.create(
            conflictId = conflictId,
            summaryText = summaryResult.summary,
            provider = summaryResult.provider
        )

        // Update conflict status
        conflictRepository.updateStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)

        logger.info("Completed summary generation for conflict $conflictId")
    }

    /**
     * Generate discussion points job: Create AI discussion points for retrospective
     */
    private suspend fun generateDiscussionPointsJob(jobId: UUID, retroId: UUID) {
        logger.info("Generating discussion points for retro $retroId")

        // Get retrospective
        val retro = retrospectiveRepository.findById(retroId)
            ?: throw IllegalStateException("Retrospective $retroId not found")

        // Get notes included in this retro
        val notes = retrospectiveRepository.getNotesForRetrospective(retroId)

        // Process unprocessed journals before generating discussion points
        // We need to get userId from retrospective_users to process journals
        val retroUsers = retrospectiveRepository.getUsersForRetrospective(retroId)
        if (retroUsers.isNotEmpty()) {
            val firstUserId = retroUsers[0]
            val partnership = partnershipRepository.findActivePartnership(firstUserId)
            if (partnership != null) {
                val partnershipId = UUID.fromString(partnership.id)
                journalContextProcessor.processUnprocessedJournals(partnershipId, firstUserId)
            }
        }

        // Call AI to generate discussion points
        val discussionPointsResult = aiProvider.generateRetroPoints(notes)

        // Format discussion points as text
        val discussionPointsText = discussionPointsResult.discussionPoints.joinToString("\n\n") { point ->
            """
            **${point.theme}**
            ${point.suggestedApproach}
            Related notes: ${point.relatedNoteIds.joinToString(", ")}
            """.trimIndent()
        }

        // Update retrospective with AI discussion points
        retrospectiveRepository.updateDiscussionPoints(retroId, discussionPointsText)

        // Update status back to IN_PROGRESS
        retrospectiveRepository.updateStatus(retroId, RetroStatus.IN_PROGRESS)

        logger.info("Completed discussion point generation for retro $retroId")
    }

    /**
     * Update partnership context job: Integrate conflict resolution into partnership history
     */
    private suspend fun updatePartnershipContextJob(jobId: UUID, conflictId: UUID, payload: String?) {
        logger.info("Updating partnership context for conflict $conflictId")

        // Get user profiles from the two partners involved
        val resolutions = resolutionRepository.findByConflict(conflictId)
        if (resolutions.size < 2) {
            throw IllegalStateException("Cannot update context: only ${resolutions.size} resolution(s) found")
        }

        val user1Id = UUID.fromString(resolutions[0].userId)

        // Get conflict and summary
        val conflict = conflictRepository.findById(conflictId, user1Id)
            ?: throw IllegalStateException("Conflict $conflictId not found")

        val summary = aiSummaryRepository.findByConflict(conflictId)
            ?: throw IllegalStateException("No summary found for conflict $conflictId")
        val user2Id = UUID.fromString(resolutions[1].userId)

        val user1 = userRepository.findById(user1Id)
            ?: throw IllegalStateException("User $user1Id not found")
        val user2 = userRepository.findById(user2Id)
            ?: throw IllegalStateException("User $user2Id not found")

        val user1Profile = me.pavekovt.ai.UserProfile(
            name = user1.name,
            age = user1.age,
            gender = user1.gender,
            description = user1.description
        )

        val user2Profile = me.pavekovt.ai.UserProfile(
            name = user2.name,
            age = user2.age,
            gender = user2.gender,
            description = user2.description
        )

        // Get existing partnership context
        val partnership = partnershipRepository.findActivePartnership(user1Id)
            ?: throw IllegalStateException("No active partnership found for users")

        val partnershipId = UUID.fromString(partnership.id)
        val existingContext = partnershipContextRepository.getContext(partnershipId)?.compactedSummary

        // Call AI to update context
        val updatedContext = aiProvider.updatePartnershipContextWithConflict(
            existingContext = existingContext,
            conflictSummary = summary.summaryText,
            user1Profile = user1Profile,
            user2Profile = user2Profile
        )

        // Save updated context
        partnershipContextRepository.upsertContext(
            partnershipId = partnershipId,
            compactedSummary = updatedContext,
            incrementConflictCount = true
        )

        logger.info("Completed partnership context update for conflict $conflictId")
    }

    fun shutdown() {
        logger.info("Shutting down job processor")
        scope.cancel()
        jobChannel.close()
    }
}

/**
 * Events emitted by job processor for SSE streaming
 */
sealed class JobEvent {
    abstract val jobId: String
    abstract val jobType: JobType
    abstract val entityId: String

    data class Started(
        override val jobId: String,
        override val jobType: JobType,
        override val entityId: String
    ) : JobEvent()

    data class Completed(
        override val jobId: String,
        override val jobType: JobType,
        override val entityId: String
    ) : JobEvent()

    data class Failed(
        override val jobId: String,
        override val jobType: JobType,
        override val entityId: String,
        val errorMessage: String
    ) : JobEvent()

    data class Retrying(
        override val jobId: String,
        override val jobType: JobType,
        override val entityId: String,
        val retryCount: Int
    ) : JobEvent()
}
