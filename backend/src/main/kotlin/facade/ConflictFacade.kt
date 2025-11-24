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
    private val partnershipContextRepository: PartnershipContextRepository,
    private val resolutionRepository: ResolutionRepository,
    private val aiProvider: AIProvider
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
     * This is the FIRST step in the new conflict resolution flow.
     * User expresses their feelings, AI provides guidance and suggests a resolution.
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

        // Check conflict is in correct status
        if (conflict.status != ConflictStatus.PENDING_FEELINGS) {
            throw IllegalStateException("Conflict is not in PENDING_FEELINGS status. Current status: ${conflict.status}")
        }

        // Get partnership context for AI
        val partnershipDTO = partnershipRepository.findActivePartnership(userId)
        val partnershipContext = if (partnershipDTO != null) {
            val partnershipId = UUID.fromString(partnershipDTO.id)
            partnershipContextRepository.getContext(partnershipId)?.compactedSummary
        } else null

        // Submit feelings and get AI guidance
        return conflictService.submitFeelings(conflictId, userId, feelingsText, partnershipContext)
    }

    /**
     * Get user's feelings for a conflict (to see AI guidance and suggested resolution)
     */
    suspend fun getFeelings(conflictId: UUID, userId: UUID): ConflictFeelingsDTO? {
        // Verify user has access to this conflict
        findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found or you don't have permission")

        return conflictService.getFeelings(conflictId, userId)
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

        // Get partnership ID for context
        val partnershipDTO = partnershipRepository.findActivePartnership(userId)
        val partnershipContext = if (partnershipDTO != null) {
            val partnershipId = UUID.fromString(partnershipDTO.id)
            partnershipContextRepository.getContext(partnershipId)?.compactedSummary
        } else null

        // Submit resolution with context
        val result = conflictService.submitResolution(conflictId, userId, resolutionText, partnershipContext)

        // Check if both resolutions are now submitted (AI summary was generated)
        val bothResolutions = resolutionRepository.getBothResolutions(conflictId)
        if (bothResolutions != null && partnershipDTO != null) {
            // AI summary was just generated, update partnership context
            val partnershipId = UUID.fromString(partnershipDTO.id)
            val existingContext = partnershipContextRepository.getContext(partnershipId)?.compactedSummary

            val updatedContext = aiProvider.updatePartnershipContext(
                existingContext = existingContext,
                newConflictSummary = result.status.name, // We'll use the conflict status for now
                newResolutions = bothResolutions
            )

            partnershipContextRepository.upsertContext(
                partnershipId = partnershipId,
                compactedSummary = updatedContext,
                incrementConflictCount = true
            )
        }

        return result
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
