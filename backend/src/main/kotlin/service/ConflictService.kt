package me.pavekovt.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.repository.*
import java.util.UUID

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

    suspend fun findById(conflictId: UUID, userId: UUID): ConflictDTO? {
        val conflict = conflictRepository.findById(conflictId) ?: return null

        // Check if user is involved in this conflict
        val isInvolved = conflict.initiatedBy == userId.toString() ||
                resolutionRepository.hasResolution(conflictId, userId)

        return if (isInvolved) conflict else null
    }

    suspend fun findByUser(userId: UUID): List<ConflictDTO> {
        return conflictRepository.findByUser(userId)
    }

    suspend fun submitResolution(
        conflictId: UUID,
        userId: UUID,
        resolutionText: String
    ): ConflictDTO {
        require(resolutionText.isNotBlank()) { "Resolution text cannot be blank" }

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
        // Check if user is involved in conflict
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        if (conflict.status != "summary_generated" &&
            conflict.status != "refinement" &&
            conflict.status != "approved"
        ) {
            throw IllegalStateException("Summary not yet generated - both partners must submit resolutions first")
        }

        val summary = aiSummaryRepository.findByConflict(conflictId)
            ?: throw IllegalStateException("Summary not found")

        // Determine which user is which for approval tracking
        val resolutions = resolutionRepository.findByConflict(conflictId)
        val userIds = resolutions.map { UUID.fromString(it.userId) }.sorted()

        if (userIds.size != 2) {
            throw IllegalStateException("Invalid conflict state")
        }

        val isUser1 = userId == userIds[0]
        val partnerId = if (isUser1) userIds[1] else userIds[0]

        // Get approval status from database
        val summaryData = aiSummaryRepository.findById(UUID.fromString(summary.id))
            ?: throw IllegalStateException("Summary not found")

        return summary.copy(
            approvedByMe = if (isUser1) summaryData.approvedByMe else summaryData.approvedByPartner,
            approvedByPartner = if (isUser1) summaryData.approvedByPartner else summaryData.approvedByMe
        )
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

    suspend fun requestRefinement(conflictId: UUID, userId: UUID) {
        // Check if user is involved
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        conflictRepository.updateStatus(conflictId, ConflictStatus.REFINEMENT)
    }

    suspend fun archive(conflictId: UUID, userId: UUID) {
        // Check if user is involved
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        conflictRepository.updateStatus(conflictId, ConflictStatus.ARCHIVED)
    }
}
