package me.pavekovt.facade

import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.ConflictFeelingsDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.repository.PartnershipContextRepository
import me.pavekovt.repository.PartnershipRepository
import me.pavekovt.repository.ResolutionRepository
import me.pavekovt.service.ConflictService
import java.util.UUID

/**
 * Facade for conflict resolution operations.
 * Orchestrates conflict workflow and enforces partnership validation.
 * Manages partnership context for AI-powered relationship advice.
 */
class ConflictFacade(
    private val conflictService: ConflictService,
    private val ownershipValidator: OwnershipValidator,
    private val partnershipRepository: PartnershipRepository,
    private val partnershipContextRepository: PartnershipContextRepository
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

    /**
     * Submit feelings/frustrations for a conflict.
     * Returns immediately with PROCESSING status. AI processes in background.
     * Users can submit multiple feelings as they process the conflict.
     */
    suspend fun submitFeelings(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String
    ): ConflictFeelingsDTO {
        require(feelingsText.isNotBlank()) { "Feelings text cannot be blank" }

        // Verify user has access to this conflict
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        // Check conflict is in correct status (PENDING_FEELINGS or PROCESSING_FEELINGS)
        if (conflict.status !in listOf(ConflictStatus.PENDING_FEELINGS, ConflictStatus.PROCESSING_FEELINGS)) {
            throw IllegalStateException("Cannot submit feelings - conflict status is ${conflict.status}")
        }

        // Get partnership context for AI
        val partnershipDTO = partnershipRepository.findActivePartnership(userId)
        val partnershipContext = if (partnershipDTO != null) {
            val partnershipId = UUID.fromString(partnershipDTO.id)
            partnershipContextRepository.getContext(partnershipId)?.compactedSummary
        } else null

        // Submit feelings - returns immediately, AI processes in background
        return conflictService.submitFeelings(conflictId, userId, feelingsText, partnershipContext)
    }

    /**
     * Get ALL feelings from this user for a conflict (users can submit multiple)
     */
    suspend fun getFeelings(conflictId: UUID, userId: UUID): List<ConflictFeelingsDTO> {
        // Verify user has access to this conflict
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        return conflictService.getFeelings(conflictId, userId)
    }

    /**
     * Get ALL feelings for a conflict from both users
     */
    suspend fun getAllFeelingsForConflict(conflictId: UUID, userId: UUID): List<ConflictFeelingsDTO> {
        // Verify user has access to this conflict
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        return conflictService.getAllFeelingsForConflict(conflictId)
    }

    /**
     * Submit resolution. Returns immediately if async summary generation needed.
     */
    suspend fun submitResolution(
        conflictId: UUID,
        userId: UUID,
        resolutionText: String
    ): ConflictDTO {
        require(resolutionText.isNotBlank()) { "Resolution text cannot be blank" }

        // Verify user has access to this conflict
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        // Check conflict is in correct status
        if (conflict.status != ConflictStatus.PENDING_RESOLUTIONS) {
            throw IllegalStateException("Cannot submit resolution - conflict status is ${conflict.status}")
        }

        // Submit resolution - if both are submitted, AI summary generation happens in background
        return conflictService.submitResolution(conflictId, userId, resolutionText)
    }

    suspend fun getSummary(conflictId: UUID, userId: UUID): AISummaryDTO {
        // Check if user is involved in conflict
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        if (conflict.status !in listOf(
                ConflictStatus.SUMMARY_GENERATED,
                ConflictStatus.REFINEMENT,
                ConflictStatus.APPROVED
            )
        ) {
            throw IllegalStateException("Summary not yet generated - current status: ${conflict.status}")
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

    suspend fun getAvailableActions(conflictId: UUID, userId: UUID): me.pavekovt.dto.ConflictActionsDTO {
        // Check if user is involved
        val conflict = findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        return conflictService.getAvailableActions(conflictId, userId)
    }
}
