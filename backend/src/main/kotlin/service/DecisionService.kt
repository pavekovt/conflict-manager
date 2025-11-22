package me.pavekovt.service

import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.DecisionStatus
import me.pavekovt.exception.NotFoundException
import me.pavekovt.repository.DecisionRepository
import java.util.UUID

class DecisionService(
    private val decisionRepository: DecisionRepository,
    private val partnershipService: PartnershipService
) {

    suspend fun create(summary: String, category: String?, userId: UUID): DecisionDTO {
        require(summary.isNotBlank()) { "Decision summary cannot be blank" }

        // Verify user has an active partnership
        partnershipService.requirePartnership(userId)

        return decisionRepository.create(null, summary, category)
    }

    suspend fun findAll(status: String?, userId: UUID): List<DecisionDTO> {
        val statusEnum = status?.let {
            runCatching { DecisionStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $status") }
        }

        // Get user's partner
        val partnerId = partnershipService.getPartnerId(userId)

        // Return decisions for this partnership only
        return decisionRepository.findByUserAndPartner(userId, partnerId, statusEnum)
    }

    suspend fun findById(id: UUID, userId: UUID): DecisionDTO {
        val partnerId = partnershipService.getPartnerId(userId)

        // Verify user has access to this decision
        val hasAccess = decisionRepository.isAccessibleByUser(id, userId, partnerId)
        if (!hasAccess) {
            throw NotFoundException()
        }

        return decisionRepository.findById(id)
            ?: throw NotFoundException()
    }

    suspend fun markReviewed(id: UUID, userId: UUID): DecisionDTO {
        val partnerId = partnershipService.getPartnerId(userId)

        // Verify user has access to this decision
        val hasAccess = decisionRepository.isAccessibleByUser(id, userId, partnerId)
        if (!hasAccess) {
            throw NotFoundException()
        }

        val success = decisionRepository.markReviewed(id)
        if (!success) {
            throw NotFoundException()
        }

        return decisionRepository.findById(id)
            ?: throw NotFoundException()
    }

    suspend fun archive(id: UUID, userId: UUID): DecisionDTO {
        val partnerId = partnershipService.getPartnerId(userId)

        // Verify user has access to this decision
        val hasAccess = decisionRepository.isAccessibleByUser(id, userId, partnerId)
        if (!hasAccess) {
            throw NotFoundException()
        }

        val success = decisionRepository.archive(id)
        if (!success) {
            throw NotFoundException()
        }

        return decisionRepository.findById(id)
            ?: throw NotFoundException()
    }
}
