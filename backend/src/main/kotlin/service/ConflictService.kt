package me.pavekovt.service

import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.repository.*
import java.util.UUID

/**
 * Simple service for conflict data operations.
 * Business logic and validation should be in ConflictFacade.
 */
class ConflictService(
    private val conflictRepository: ConflictRepository,
    private val resolutionRepository: ResolutionRepository,
    private val aiSummaryRepository: AISummaryRepository,
    private val decisionRepository: DecisionRepository,
    private val aiProvider: AIProvider
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
            // Generate AI summary
            val summary = aiProvider.summarizeConflict(
                bothResolutions.first,
                bothResolutions.second
            )

            // Save summary
            aiSummaryRepository.create(conflictId, summary.summary, summary.provider)

            // Update conflict status
            conflictRepository.updateStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)
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
            conflictRepository.updateStatus(UUID.fromString(summary.conflictId), ConflictStatus.APPROVED)
        }
    }

    suspend fun requestRefinement(conflictId: UUID) {
        conflictRepository.updateStatus(conflictId, ConflictStatus.REFINEMENT)
    }

    suspend fun archive(conflictId: UUID) {
        conflictRepository.updateStatus(conflictId, ConflictStatus.ARCHIVED)
    }
}
