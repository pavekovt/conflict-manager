package me.pavekovt.facade

import me.pavekovt.dto.JournalEntryDTO
import me.pavekovt.entity.JournalStatus
import me.pavekovt.repository.PartnershipRepository
import me.pavekovt.service.JournalService
import java.util.UUID

/**
 * Facade for journal operations.
 * Enforces ownership validation - users can only access their own journal entries.
 */
class JournalFacade(
    private val journalService: JournalService,
    private val ownershipValidator: OwnershipValidator,
    private val partnershipRepository: PartnershipRepository
) {

    suspend fun create(content: String, userId: UUID): JournalEntryDTO {
        // Verify user has an active partnership
        ownershipValidator.requirePartnership(userId)

        val partnership = partnershipRepository.findActivePartnership(userId)
            ?: throw IllegalStateException("No active partnership found")
        val partnershipId = UUID.fromString(partnership.id)

        return journalService.create(userId, partnershipId, content)
    }

    suspend fun findById(id: UUID, userId: UUID): JournalEntryDTO {
        val journal = journalService.findById(id)
            ?: throw IllegalStateException("Journal entry not found")

        // Verify ownership
        if (journal.userId != userId.toString()) {
            throw IllegalStateException("You don't have access to this journal entry")
        }

        return journal
    }

    suspend fun findMyJournals(
        userId: UUID,
        status: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<JournalEntryDTO> {
        val journalStatus = status?.let {
            try {
                JournalStatus.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        return journalService.findByUser(userId, journalStatus, limit, offset)
    }

    suspend fun update(id: UUID, content: String, userId: UUID): Boolean {
        val journal = journalService.findById(id)
            ?: throw IllegalStateException("Journal entry not found")

        // Verify ownership
        if (journal.userId != userId.toString()) {
            throw IllegalStateException("You don't have access to this journal entry")
        }

        // Can only update drafts
        if (journal.status != JournalStatus.DRAFT.name.lowercase()) {
            throw IllegalStateException("Can only update draft journal entries")
        }

        return journalService.update(id, content)
    }

    suspend fun complete(id: UUID, userId: UUID): Boolean {
        val journal = journalService.findById(id)
            ?: throw IllegalStateException("Journal entry not found")

        // Verify ownership
        if (journal.userId != userId.toString()) {
            throw IllegalStateException("You don't have access to this journal entry")
        }

        // Can only complete drafts
        if (journal.status != JournalStatus.DRAFT.name.lowercase()) {
            throw IllegalStateException("Journal entry is already completed")
        }

        return journalService.complete(id)
    }

    suspend fun archive(id: UUID, userId: UUID): Boolean {
        val journal = journalService.findById(id)
            ?: throw IllegalStateException("Journal entry not found")

        // Verify ownership
        if (journal.userId != userId.toString()) {
            throw IllegalStateException("You don't have access to this journal entry")
        }

        return journalService.archive(id)
    }

    suspend fun delete(id: UUID, userId: UUID): Boolean {
        val journal = journalService.findById(id)
            ?: throw IllegalStateException("Journal entry not found")

        // Verify ownership
        if (journal.userId != userId.toString()) {
            throw IllegalStateException("You don't have access to this journal entry")
        }

        return journalService.delete(id)
    }
}
