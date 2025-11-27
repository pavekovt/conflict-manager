package me.pavekovt.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.pavekovt.dto.JobDTO
import me.pavekovt.entity.*
import me.pavekovt.repository.JobRepository
import me.pavekovt.service.job.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Background job processor that handles async AI operations using Kotlin Channels.
 * Delegates job processing to specialized handlers.
 * Publishes results via SharedFlow for SSE streaming to clients.
 */
class JobProcessorService(
    private val jobRepository: JobRepository,
    private val processFeelingsHandler: ProcessFeelingsJobHandler,
    private val generateSummaryHandler: GenerateSummaryJobHandler,
    private val generateDiscussionPointsHandler: GenerateDiscussionPointsJobHandler,
    private val updatePartnershipContextHandler: UpdatePartnershipContextJobHandler
) {
    private val logger = LoggerFactory.getLogger(JobProcessorService::class.java)
    private val jobChannel = Channel<UUID>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // SSE event streams for real-time updates
    private val _eventFlow = MutableSharedFlow<JobEvent>(replay = 0, extraBufferCapacity = 100)
    val eventFlow: SharedFlow<JobEvent> = _eventFlow.asSharedFlow()

    init {
        logger.info("Initializing JobProcessorService with 3 workers")

        // Start worker coroutines
        repeat(3) { workerId ->
            scope.launch {
                logger.info("Worker-{} started and ready", workerId)
                processJobsWorker(workerId)
            }
        }

        // Start job poller for orphaned jobs
        scope.launch {
            logger.info("Job poller started")
            pollPendingJobs()
        }

        logger.info("JobProcessorService initialization complete")
    }

    /**
     * Queue a new job for processing
     */
    suspend fun queueJob(jobId: UUID) {
        logger.debug("Queueing job {}", jobId)
        jobChannel.send(jobId)
    }

    /**
     * Worker coroutine that processes jobs from the channel
     */
    private suspend fun processJobsWorker(workerId: Int) {
        for (jobId in jobChannel) {
            try {
                logger.debug("Worker-{} picked up job {}", workerId, jobId)
                processJob(jobId, workerId)
            } catch (e: Exception) {
                logger.error("Worker-{} encountered unexpected error processing job {}", workerId, jobId, e)
            }
        }
        logger.warn("Worker-{} shutting down", workerId)
    }

    /**
     * Poll database for pending jobs (recovery mechanism for server restarts)
     */
    private suspend fun pollPendingJobs() {
        while (true) {
            try {
                val pendingJobs = jobRepository.findPendingJobs(limit = 10)
                if (pendingJobs.isNotEmpty()) {
                    logger.info("Found {} pending jobs, queueing for processing", pendingJobs.size)
                    pendingJobs.forEach { job ->
                        queueJob(UUID.fromString(job.id))
                    }
                }
                delay(5000) // Poll every 5 seconds
            } catch (e: Exception) {
                logger.error("Error in job poller", e)
                delay(10000) // Back off on error
            }
        }
    }

    /**
     * Process a single job based on its type
     */
    private suspend fun processJob(jobId: UUID, workerId: Int) {
        val job = jobRepository.findById(jobId)
        if (job == null) {
            logger.error("Worker-{}: Job {} not found", workerId, jobId)
            return
        }

        // Skip if already processing or completed
        if (job.status != JobStatus.PENDING) {
            logger.debug("Worker-{}: Job {} already processed (status: {})", workerId, jobId, job.status)
            return
        }

        logger.info("Worker-{}: Processing {} job for entity {}", workerId, job.jobType, job.entityId)

        try {
            // Mark as started and emit event
            jobRepository.markAsStarted(jobId)
            _eventFlow.emit(JobEvent.Started(jobId.toString(), job.jobType, job.entityId))

            // Delegate to appropriate handler
            delegateToHandler(job)

            // Mark as completed
            jobRepository.markAsCompleted(jobId)
            _eventFlow.emit(JobEvent.Completed(jobId.toString(), job.jobType, job.entityId))

            logger.info("Worker-{}: Job {} completed successfully", workerId, jobId)

        } catch (e: Exception) {
            logger.error("Worker-{}: Job {} failed - {}: {}", workerId, jobId, e.javaClass.simpleName, e.message)
            handleJobFailure(job, jobId, e)
        }
    }

    /**
     * Delegate job processing to appropriate handler
     */
    private suspend fun delegateToHandler(job: JobDTO) {
        val entityId = UUID.fromString(job.entityId)

        when (job.jobType) {
            JobType.PROCESS_FEELINGS -> {
                logger.debug("Delegating to ProcessFeelingsHandler")
                processFeelingsHandler.process(entityId, job.payload)
            }
            JobType.GENERATE_SUMMARY -> {
                logger.debug("Delegating to GenerateSummaryHandler")
                generateSummaryHandler.process(entityId)
            }
            JobType.GENERATE_DISCUSSION_POINTS -> {
                logger.debug("Delegating to GenerateDiscussionPointsHandler")
                generateDiscussionPointsHandler.process(entityId)
            }
            JobType.UPDATE_PARTNERSHIP_CONTEXT -> {
                logger.debug("Delegating to UpdatePartnershipContextHandler")
                updatePartnershipContextHandler.process(entityId, job.payload)
            }
        }
    }

    /**
     * Handle job failure with retry logic
     */
    private suspend fun handleJobFailure(job: JobDTO, jobId: UUID, exception: Exception) {
        val errorMessage = exception.message ?: "Unknown error"

        // Retry logic
        if (job.retryCount < 3) {
            val nextRetry = job.retryCount + 1
            logger.info("Retrying job {} (attempt {}/3)", jobId, nextRetry)

            jobRepository.incrementRetryCount(jobId)
            jobRepository.updateStatus(jobId, JobStatus.PENDING) // Re-queue
            queueJob(jobId)

            _eventFlow.emit(JobEvent.Retrying(jobId.toString(), job.jobType, job.entityId, nextRetry))
        } else {
            logger.error("Job {} exceeded max retries (3), marking as FAILED", jobId)
            jobRepository.markAsFailed(jobId, errorMessage)
            _eventFlow.emit(JobEvent.Failed(jobId.toString(), job.jobType, job.entityId, errorMessage))
        }
    }

    /**
     * Shutdown the job processor gracefully
     */
    fun shutdown() {
        logger.info("Shutting down JobProcessorService")
        scope.cancel()
        jobChannel.close()
        logger.info("JobProcessorService shutdown complete")
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
