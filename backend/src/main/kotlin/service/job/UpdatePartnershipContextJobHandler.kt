package me.pavekovt.service.job

import me.pavekovt.ai.AIProvider
import me.pavekovt.repository.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Handles UPDATE_PARTNERSHIP_CONTEXT job type
 */
class UpdatePartnershipContextJobHandler(
    private val resolutionRepository: ResolutionRepository,
    private val conflictRepository: ConflictRepository,
    private val aiSummaryRepository: AISummaryRepository,
    private val partnershipRepository: PartnershipRepository,
    private val partnershipContextRepository: PartnershipContextRepository,
    private val aiProvider: AIProvider,
    private val userProfileLoader: UserProfileLoader
) {
    private val logger = LoggerFactory.getLogger(UpdatePartnershipContextJobHandler::class.java)

    suspend fun process(conflictId: UUID, payload: String?) {
        logger.info("[CONTEXT_UPDATE] Starting partnership context update for conflict {}", conflictId)

        // Load resolutions
        val resolutions = resolutionRepository.findByConflict(conflictId)
        if (resolutions.size < 2) {
            throw IllegalStateException("Cannot update context: only ${resolutions.size} resolution(s) found")
        }

        val user1Id = UUID.fromString(resolutions[0].userId)
        val user2Id = UUID.fromString(resolutions[1].userId)

        logger.debug("[CONTEXT_UPDATE] Users: {} and {}", user1Id, user2Id)

        // Load conflict
        val conflict = conflictRepository.findById(conflictId, user1Id)
            ?: throw IllegalStateException("Conflict $conflictId not found")

        // Load summary
        val summary = aiSummaryRepository.findByConflict(conflictId)
            ?: throw IllegalStateException("No summary found for conflict $conflictId")

        // Load user profiles
        val (user1Profile, user2Profile) = userProfileLoader.loadProfiles(user1Id, user2Id)

        // Load existing partnership context
        val partnership = partnershipRepository.findActivePartnership(user1Id)
            ?: throw IllegalStateException("No active partnership found for users")

        val partnershipId = UUID.fromString(partnership.id)
        val existingContext = partnershipContextRepository.getContext(partnershipId)?.compactedSummary

        logger.info("[CONTEXT_UPDATE] Calling AI provider to update partnership context")

        // Call AI to update context
        val updatedContext = aiProvider.updatePartnershipContextWithConflict(
            existingContext = existingContext,
            conflictSummary = summary.summaryText,
            user1Profile = user1Profile,
            user2Profile = user2Profile
        )

        logger.info("[CONTEXT_UPDATE] Saving updated context to database")

        // Save updated context
        partnershipContextRepository.upsertContext(
            partnershipId = partnershipId,
            compactedSummary = updatedContext,
            incrementConflictCount = true
        )

        logger.info("[CONTEXT_UPDATE] Partnership context update completed for conflict {}", conflictId)
    }
}
