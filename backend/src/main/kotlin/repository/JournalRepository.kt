package me.pavekovt.repository

import me.pavekovt.dto.JournalEntryDTO
import me.pavekovt.entity.JournalStatus
import java.util.UUID

interface JournalRepository {
    suspend fun create(userId: UUID, partnershipId: UUID, content: String): JournalEntryDTO
    suspend fun findById(id: UUID): JournalEntryDTO?
    suspend fun findByUser(userId: UUID, status: JournalStatus? = null, limit: Int = 20, offset: Int = 0): List<JournalEntryDTO>
    suspend fun update(id: UUID, content: String): Boolean
    suspend fun updateStatus(id: UUID, status: JournalStatus): Boolean
    suspend fun complete(id: UUID): Boolean
    suspend fun delete(id: UUID): Boolean
    suspend fun findUnprocessedByPartnership(partnershipId: UUID): List<JournalEntryDTO>
    suspend fun markAsProcessed(journalIds: List<UUID>): Boolean
}
