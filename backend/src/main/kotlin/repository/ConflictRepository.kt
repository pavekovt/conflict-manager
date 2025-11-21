package me.pavekovt.repository

import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.ConflictStatus
import java.util.UUID

interface ConflictRepository {
    suspend fun create(initiatedBy: UUID): ConflictDTO
    suspend fun findById(conflictId: UUID): ConflictDTO?
    suspend fun findByUser(userId: UUID): List<ConflictDTO>
    suspend fun updateStatus(conflictId: UUID, newStatus: ConflictStatus): Boolean
    suspend fun getPartnerUserId(conflictId: UUID, currentUserId: UUID): UUID?
}
