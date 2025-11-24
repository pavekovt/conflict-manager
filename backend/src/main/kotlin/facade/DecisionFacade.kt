package me.pavekovt.facade

import me.pavekovt.dto.DecisionDTO
import me.pavekovt.exception.NotFoundException
import me.pavekovt.service.DecisionService
import java.util.UUID

/**
 * Facade for decision operations.
 * Orchestrates decision workflow and enforces partnership validation.
 */
class DecisionFacade(
    private val decisionService: DecisionService,
    private val ownershipValidator: OwnershipValidator
) {

    suspend fun create(summary: String, category: String?, userId: UUID): DecisionDTO {
        require(summary.isNotBlank()) { "Decision summary cannot be blank" }

        // Verify user has an active partnership
        ownershipValidator.requirePartnership(userId)

        return decisionService.create(summary, category)
    }

    suspend fun findAll(status: String?, userId: UUID): List<DecisionDTO> {
        // Get user's partner
        val partnerId = ownershipValidator.getPartnerId(userId)

        // Return decisions for this partnership only
        return decisionService.findByUserAndPartner(userId, partnerId, status)
    }

    suspend fun findById(id: UUID, userId: UUID): DecisionDTO {
        val partnerId = ownershipValidator.getPartnerId(userId)

        // Verify user has access to this decision
        val hasAccess = decisionService.isAccessibleByUser(id, userId, partnerId)
        if (!hasAccess) {
            throw NotFoundException()
        }

        return decisionService.findById(id)
    }

    suspend fun markReviewed(id: UUID, userId: UUID): DecisionDTO {
        val partnerId = ownershipValidator.getPartnerId(userId)

        // Verify user has access to this decision
        val hasAccess = decisionService.isAccessibleByUser(id, userId, partnerId)
        if (!hasAccess) {
            throw IllegalStateException("Decision not found or you don't have permission")
        }

        return decisionService.markReviewed(id)
    }

    suspend fun archive(id: UUID, userId: UUID): DecisionDTO {
        val partnerId = ownershipValidator.getPartnerId(userId)

        // Verify user has access to this decision
        val hasAccess = decisionService.isAccessibleByUser(id, userId, partnerId)
        if (!hasAccess) {
            throw IllegalStateException("Decision not found or you don't have permission")
        }

        return decisionService.archive(id)
    }
}
