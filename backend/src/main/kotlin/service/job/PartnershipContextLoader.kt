package me.pavekovt.service.job

import me.pavekovt.repository.PartnershipContextRepository
import me.pavekovt.repository.PartnershipRepository
import me.pavekovt.service.JournalContextProcessor
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Helper service for loading and updating partnership context
 */
class PartnershipContextLoader(
    private val partnershipRepository: PartnershipRepository,
    private val partnershipContextRepository: PartnershipContextRepository,
    private val journalContextProcessor: JournalContextProcessor
) {
    private val logger = LoggerFactory.getLogger(PartnershipContextLoader::class.java)

    /**
     * Load partnership context for a user, processing journals if needed
     */
    suspend fun loadContext(userId: UUID, processJournals: Boolean = true): String? {
        logger.debug("Loading partnership context for user {}", userId)

        val partnership = partnershipRepository.findActivePartnership(userId)
            ?: return null

        val partnershipId = UUID.fromString(partnership.id)

        // Process unprocessed journals before returning context
        if (processJournals) {
            logger.debug("Processing unprocessed journals for partnership {}", partnershipId)
            journalContextProcessor.processUnprocessedJournals(partnershipId, userId)
        }

        return partnershipContextRepository.getContext(partnershipId)?.compactedSummary
    }

    /**
     * Get partnership ID for a user
     */
    suspend fun getPartnershipId(userId: UUID): UUID? {
        val partnership = partnershipRepository.findActivePartnership(userId)
        return partnership?.let { UUID.fromString(it.id) }
    }
}
