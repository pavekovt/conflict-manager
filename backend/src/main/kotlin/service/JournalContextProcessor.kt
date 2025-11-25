package me.pavekovt.service

import me.pavekovt.ai.AIProvider
import me.pavekovt.ai.JournalEntryWithTimestamp
import me.pavekovt.ai.UserProfile
import me.pavekovt.dto.JournalEntryDTO
import me.pavekovt.repository.PartnershipContextRepository
import me.pavekovt.repository.PartnershipRepository
import me.pavekovt.repository.UserRepository
import java.util.UUID

/**
 * Helper service to process unprocessed journals and update partnership context.
 * Called before AI interactions to ensure context is up-to-date.
 */
class JournalContextProcessor(
    private val journalService: JournalService,
    private val partnershipContextRepository: PartnershipContextRepository,
    private val partnershipRepository: PartnershipRepository,
    private val userRepository: UserRepository,
    private val aiProvider: AIProvider
) {

    /**
     * Process all unprocessed journals for a partnership and update context.
     * Returns true if context was updated, false if no new journals to process.
     */
    suspend fun processUnprocessedJournals(partnershipId: UUID, currentUserId: UUID): Boolean {
        // Get unprocessed journals
        val unprocessedJournals = journalService.findUnprocessedByPartnership(partnershipId)

        if (unprocessedJournals.isEmpty()) {
            return false // No new journals to process
        }

        // Get partnership info to find partner
        val partnership = partnershipRepository.findActivePartnership(currentUserId)
            ?: throw IllegalStateException("No active partnership found")

        val partnerId = UUID.fromString(partnership.partnerId)

        // Get user profiles
        val currentUser = userRepository.findById(currentUserId)
            ?: throw IllegalStateException("Current user not found")
        val partner = userRepository.findById(partnerId)
            ?: throw IllegalStateException("Partner not found")

        val currentUserProfile = UserProfile(
            name = currentUser.name,
            age = currentUser.age,
            gender = currentUser.gender,
            description = currentUser.description
        )

        val partnerProfile = UserProfile(
            name = partner.name,
            age = partner.age,
            gender = partner.gender,
            description = partner.description
        )

        // Separate journals by user
        val currentUserJournals = unprocessedJournals
            .filter { it.userId == currentUserId.toString() }
            .map { it.toJournalEntryWithTimestamp() }

        val partnerJournals = unprocessedJournals
            .filter { it.userId == partnerId.toString() }
            .map { it.toJournalEntryWithTimestamp() }

        // Get existing context
        val existingContext = partnershipContextRepository.getContext(partnershipId)?.compactedSummary

        // Update context with AI
        val updatedContext = aiProvider.updatePartnershipContextWithJournals(
            existingContext = existingContext,
            user1Journals = currentUserJournals,
            user2Journals = partnerJournals,
            user1Profile = currentUserProfile,
            user2Profile = partnerProfile
        )

        // Save updated context
        partnershipContextRepository.upsertContext(
            partnershipId = partnershipId,
            compactedSummary = updatedContext,
            incrementRetroCount = false,
            incrementConflictCount = false
        )

        // Mark journals as processed
        val journalIds = unprocessedJournals.map { UUID.fromString(it.id) }
        journalService.markAsProcessed(journalIds)

        return true
    }

    private fun JournalEntryDTO.toJournalEntryWithTimestamp() = JournalEntryWithTimestamp(
        content = this.content,
        createdAt = this.createdAt,
        completedAt = this.completedAt
    )
}
