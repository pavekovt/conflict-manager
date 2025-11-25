package me.pavekovt.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.ConflictFeelingsDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.entity.JobType
import me.pavekovt.repository.*
import java.util.UUID

/**
 * Service for conflict data operations with async AI processing.
 */
class ConflictService(
    private val conflictRepository: ConflictRepository,
    private val conflictFeelingsRepository: ConflictFeelingsRepository,
    private val resolutionRepository: ResolutionRepository,
    private val aiSummaryRepository: AISummaryRepository,
    private val decisionRepository: DecisionRepository,
    private val jobRepository: JobRepository,
    private val jobProcessorService: JobProcessorService
) {

    suspend fun create(userId: UUID): ConflictDTO {
        return conflictRepository.create(userId)
    }

    suspend fun findById(conflictId: UUID): ConflictDTO? {
        return conflictRepository.findById(conflictId)
    }

    suspend fun findByUser(userId: UUID, partnersIds: List<UUID>): List<ConflictDTO> {
        return conflictRepository.findByUser(userId, partnersIds)
    }

    /**
     * Submit feelings for a conflict. Returns immediately with PROCESSING status.
     * AI processing happens in background via JobProcessorService.
     */
    suspend fun submitFeelings(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String,
        partnershipContext: String? = null
    ): ConflictFeelingsDTO {
        // Create feelings entry with PROCESSING status
        val feelings = conflictFeelingsRepository.create(
            conflictId = conflictId,
            userId = userId,
            feelingsText = feelingsText
        )

        // Create background job for AI processing
        val payload = buildJsonObject {
            put("partnershipContext", partnershipContext)
        }.toString()

        val job = jobRepository.create(
            jobType = JobType.PROCESS_FEELINGS,
            entityId = UUID.fromString(feelings.id),
            payload = payload
        )

        // Queue job for processing
        jobProcessorService.queueJob(UUID.fromString(job.id))

        return feelings
    }

    /**
     * Get all feelings for a user in a specific conflict
     */
    suspend fun getFeelings(conflictId: UUID, userId: UUID): List<ConflictFeelingsDTO> {
        return conflictFeelingsRepository.findByConflictAndUser(conflictId, userId)
    }

    /**
     * Get all feelings for a conflict (both users)
     */
    suspend fun getAllFeelingsForConflict(conflictId: UUID): List<ConflictFeelingsDTO> {
        return conflictFeelingsRepository.findByConflict(conflictId)
    }

    /**
     * Submit resolution. If both resolutions submitted, queue AI summary generation job.
     */
    suspend fun submitResolution(
        conflictId: UUID,
        userId: UUID,
        resolutionText: String
    ): ConflictDTO {
        // Check if user already submitted
        if (resolutionRepository.hasResolution(conflictId, userId)) {
            throw IllegalStateException("You have already submitted a resolution for this conflict")
        }

        // Save resolution
        resolutionRepository.create(conflictId, userId, resolutionText)

        // Check if both resolutions are now submitted
        val bothResolutions = resolutionRepository.getBothResolutions(conflictId)

        if (bothResolutions != null) {
            // Update conflict status to PROCESSING_SUMMARY
            conflictRepository.updateStatus(conflictId, ConflictStatus.PROCESSING_SUMMARY)

            // Create background job for AI summary generation
            val job = jobRepository.create(
                jobType = JobType.GENERATE_SUMMARY,
                entityId = conflictId
            )

            // Queue job for processing
            jobProcessorService.queueJob(UUID.fromString(job.id))
        }

        return conflictRepository.findById(conflictId)
            ?: throw IllegalStateException("Conflict not found")
    }

    suspend fun getSummary(conflictId: UUID, userId: UUID): AISummaryDTO {
        // Get both users involved in the conflict
        val resolutions = resolutionRepository.findByConflict(conflictId)
        val allUserIds = resolutions.map { UUID.fromString(it.userId) }

        if (allUserIds.size != 2) {
            throw IllegalStateException("Invalid conflict state")
        }

        // Get partner user ID (the other user in the conflict)
        val partnerUserId = allUserIds.first { it != userId }

        // Get summary with proper approval status for this user
        return aiSummaryRepository.findByConflictForUser(conflictId, userId, partnerUserId)
            ?: throw IllegalStateException("Summary not found")
    }

    suspend fun approveSummary(summaryId: UUID, userId: UUID, conflictId: UUID) {
        // Approve by this user
        aiSummaryRepository.approve(summaryId, userId, conflictId)

        // Check if both approved
        if (aiSummaryRepository.isApprovedByBoth(summaryId)) {
            val summary = aiSummaryRepository.findById(summaryId)
                ?: throw IllegalStateException("Summary not found")

            // Create decision
            decisionRepository.create(
                conflictId = UUID.fromString(summary.conflictId),
                summary = summary.summaryText,
                category = null
            )

            // Update conflict to approved
            val conflictUUID = UUID.fromString(summary.conflictId)
            conflictRepository.updateStatus(conflictUUID, ConflictStatus.APPROVED)

            // Queue partnership context update job (async)
            val job = jobRepository.create(
                jobType = JobType.UPDATE_PARTNERSHIP_CONTEXT,
                entityId = conflictUUID
            )
            jobProcessorService.queueJob(UUID.fromString(job.id))
        }
    }

    suspend fun requestRefinement(conflictId: UUID) {
        conflictRepository.updateStatus(conflictId, ConflictStatus.REFINEMENT)
    }

    suspend fun archive(conflictId: UUID) {
        conflictRepository.updateStatus(conflictId, ConflictStatus.ARCHIVED)
    }
}
