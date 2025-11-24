package me.pavekovt.facade

import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.service.ConflictService
import java.util.UUID

/**
 * Facade for conflict resolution operations.
 * Orchestrates conflict workflow and enforces partnership validation.
 */
class ConflictFacade(
    private val conflictService: ConflictService,
    private val ownershipValidator: OwnershipValidator
) {

    suspend fun create(userId: UUID): ConflictDTO {
        // Verify user has an active partnership
        ownershipValidator.requirePartnership(userId)
        return conflictService.create(userId)
    }

    suspend fun findById(conflictId: UUID, userId: UUID): ConflictDTO? {
        val conflict = conflictService.findById(conflictId) ?: return null

        // Verify user has access to this conflict (must be partner with initiator)
        val initiatorId = UUID.fromString(conflict.initiatedBy)
        if (ownershipValidator.hasAccess(initiatorId, userId)) {
            return conflict
        }
        return null
    }

    suspend fun findByUser(userId: UUID): List<ConflictDTO> {
        return conflictService.findByUser(userId, ownershipValidator.getHistoricalPartnerIds(userId))
    }

    suspend fun submitResolution(
        conflictId: UUID,
        userId: UUID,
        resolutionText: String
    ): ConflictDTO {
        require(resolutionText.isNotBlank()) { "Resolution text cannot be blank" }

        // Verify user has access to this conflict
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        return conflictService.submitResolution(conflictId, userId, resolutionText)
    }

    suspend fun getSummary(conflictId: UUID, userId: UUID): AISummaryDTO {
        // Check if user is involved in conflict
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        if (conflict.status !in listOf(ConflictStatus.SUMMARY_GENERATED, ConflictStatus.REFINEMENT, ConflictStatus.APPROVED)) {
            throw IllegalStateException("Summary not yet generated - both partners must submit resolutions first")
        }

        return conflictService.getSummary(conflictId, userId)
    }

    suspend fun approveSummary(summaryId: UUID, userId: UUID, conflictId: UUID) {
        // Verify user has access via findById
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        conflictService.approveSummary(summaryId, userId, conflictId)
    }

    suspend fun requestRefinement(conflictId: UUID, userId: UUID) {
        // Check if user is involved
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        conflictService.requestRefinement(conflictId)
    }

    suspend fun archive(conflictId: UUID, userId: UUID) {
        // Check if user is involved
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        conflictService.archive(conflictId)
    }
}
