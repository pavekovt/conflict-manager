package me.pavekovt.service

import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.DecisionStatus
import me.pavekovt.exception.NotFoundException
import me.pavekovt.repository.DecisionRepository
import java.util.UUID

/**
 * Simple service for decision data operations.
 * Business logic and validation should be in DecisionFacade.
 */
class DecisionService(
    private val decisionRepository: DecisionRepository
) {

    suspend fun create(summary: String, category: String?): DecisionDTO {
        return decisionRepository.create(null, summary, category)
    }

    suspend fun findAll(status: DecisionStatus?): List<DecisionDTO> {
        return decisionRepository.findAll(status)
    }

    suspend fun findByUserAndPartner(userId: UUID, partnerId: UUID, status: String?): List<DecisionDTO> {
        val statusEnum = status?.let {
            runCatching { DecisionStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $status") }
        }

        return decisionRepository.findByUserAndPartner(userId, partnerId, statusEnum)
    }

    suspend fun findById(id: UUID): DecisionDTO {
        return decisionRepository.findById(id)
            ?: throw NotFoundException()
    }

    suspend fun isAccessibleByUser(decisionId: UUID, userId: UUID, partnerId: UUID): Boolean {
        return decisionRepository.isAccessibleByUser(decisionId, userId, partnerId)
    }

    suspend fun markReviewed(id: UUID): DecisionDTO {
        val success = decisionRepository.markReviewed(id)
        if (!success) {
            throw NotFoundException()
        }

        return decisionRepository.findById(id)
            ?: throw NotFoundException()
    }

    suspend fun archive(id: UUID): DecisionDTO {
        val success = decisionRepository.archive(id)
        if (!success) {
            throw NotFoundException()
        }

        return decisionRepository.findById(id)
            ?: throw NotFoundException()
    }
}
