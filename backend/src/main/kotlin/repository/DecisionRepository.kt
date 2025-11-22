package me.pavekovt.repository

import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.DecisionStatus
import java.util.UUID

interface DecisionRepository {
    suspend fun create(conflictId: UUID?, summary: String, category: String?): DecisionDTO
    suspend fun findAll(status: DecisionStatus? = null): List<DecisionDTO>
    suspend fun findByUserAndPartner(userId: UUID, partnerId: UUID, status: DecisionStatus? = null): List<DecisionDTO>
    suspend fun findById(id: UUID): DecisionDTO?
    suspend fun isAccessibleByUser(decisionId: UUID, userId: UUID, partnerId: UUID): Boolean
    suspend fun markReviewed(id: UUID): Boolean
    suspend fun archive(id: UUID): Boolean
}
