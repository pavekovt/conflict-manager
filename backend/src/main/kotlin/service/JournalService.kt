package me.pavekovt.service

import me.pavekovt.dto.JournalEntryDTO
import me.pavekovt.entity.JournalStatus
import me.pavekovt.repository.JournalRepository
import java.util.UUID

/**
 * Service for journal entry operations.
 * Business logic and validation should be in JournalFacade.
 */
class JournalService(
    private val journalRepository: JournalRepository
) {

    suspend fun create(userId: UUID, partnershipId: UUID, content: String): JournalEntryDTO {
        require(content.isNotBlank()) { "Journal content cannot be blank" }
        return journalRepository.create(userId, partnershipId, content)
    }

    suspend fun findById(id: UUID): JournalEntryDTO? {
        return journalRepository.findById(id)
    }

    suspend fun findByUser(
        userId: UUID,
        status: JournalStatus? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<JournalEntryDTO> {
        return journalRepository.findByUser(userId, status, limit, offset)
    }

    suspend fun update(id: UUID, content: String): Boolean {
        require(content.isNotBlank()) { "Journal content cannot be blank" }
        return journalRepository.update(id, content)
    }

    suspend fun complete(id: UUID): Boolean {
        return journalRepository.complete(id)
    }

    suspend fun archive(id: UUID): Boolean {
        return journalRepository.updateStatus(id, JournalStatus.ARCHIVED)
    }

    suspend fun delete(id: UUID): Boolean {
        return journalRepository.delete(id)
    }

    suspend fun findUnprocessedByPartnership(partnershipId: UUID): List<JournalEntryDTO> {
        return journalRepository.findUnprocessedByPartnership(partnershipId)
    }

    suspend fun markAsProcessed(journalIds: List<UUID>): Boolean {
        return journalRepository.markAsProcessed(journalIds)
    }
}
