package me.pavekovt.service

import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.DecisionStatus
import me.pavekovt.repository.DecisionRepository
import java.util.UUID

class DecisionService(
    private val decisionRepository: DecisionRepository
) {

    suspend fun create(summary: String, category: String?): DecisionDTO {
        require(summary.isNotBlank()) { "Decision summary cannot be blank" }

        return decisionRepository.create(null, summary, category)
    }

    suspend fun findAll(status: String?): List<DecisionDTO> {
        val statusEnum = status?.let {
            runCatching { DecisionStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $status") }
        }

        return decisionRepository.findAll(statusEnum)
    }

    suspend fun findById(id: UUID): DecisionDTO {
        return decisionRepository.findById(id)
            ?: throw IllegalStateException("Decision not found")
    }

    suspend fun markReviewed(id: UUID): DecisionDTO {
        val success = decisionRepository.markReviewed(id)
        if (!success) {
            throw IllegalStateException("Decision not found")
        }

        return decisionRepository.findById(id)
            ?: throw IllegalStateException("Decision not found")
    }

    suspend fun archive(id: UUID): DecisionDTO {
        val success = decisionRepository.archive(id)
        if (!success) {
            throw IllegalStateException("Decision not found")
        }

        return decisionRepository.findById(id)
            ?: throw IllegalStateException("Decision not found")
    }
}
