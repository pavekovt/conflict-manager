package me.pavekovt.service.job

import me.pavekovt.ai.AIProvider
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.repository.AISummaryRepository
import me.pavekovt.repository.ConflictRepository
import me.pavekovt.repository.ResolutionRepository
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Handles GENERATE_SUMMARY job type
 */
class GenerateSummaryJobHandler(
    private val resolutionRepository: ResolutionRepository,
    private val aiSummaryRepository: AISummaryRepository,
    private val conflictRepository: ConflictRepository,
    private val aiProvider: AIProvider,
    private val userProfileLoader: UserProfileLoader,
    private val contextLoader: PartnershipContextLoader
) {
    private val logger = LoggerFactory.getLogger(GenerateSummaryJobHandler::class.java)

    suspend fun process(conflictId: UUID) {
        logger.info("[SUMMARY] Starting summary generation for conflict {}", conflictId)

        // Load both resolutions
        val resolutions = resolutionRepository.findByConflict(conflictId)
        if (resolutions.size < 2) {
            throw IllegalStateException("Cannot generate summary: only ${resolutions.size} resolution(s) found")
        }

        val resolution1 = resolutions[0]
        val resolution2 = resolutions[1]
        val user1Id = UUID.fromString(resolution1.userId)
        val user2Id = UUID.fromString(resolution2.userId)

        logger.debug("[SUMMARY] Resolutions from users {} and {}", user1Id, user2Id)

        // Load user profiles
        val (user1Profile, user2Profile) = userProfileLoader.loadProfiles(user1Id, user2Id)

        // Load partnership context (with journal processing)
        val partnershipContext = contextLoader.loadContext(user1Id, processJournals = true)

        // Detect language
        val detectedLanguage = aiProvider.detectLanguage(
            resolution1.resolutionText + " " + resolution2.resolutionText
        )
        logger.debug("[SUMMARY] Detected language: {}", detectedLanguage)

        logger.info("[SUMMARY] Calling AI provider for summary generation")

        // Call AI to generate summary
        val summaryResult = aiProvider.summarizeConflict(
            resolution1 = resolution1.resolutionText,
            resolution2 = resolution2.resolutionText,
            user1Profile = user1Profile,
            user2Profile = user2Profile,
            partnershipContext = partnershipContext,
            detectedLanguage = detectedLanguage
        )

        logger.info("[SUMMARY] AI summary generated, saving to database")

        // Save AI summary
        aiSummaryRepository.create(
            conflictId = conflictId,
            summaryText = summaryResult.summary,
            provider = summaryResult.provider
        )

        // Update conflict status
        conflictRepository.updateStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)

        logger.info("[SUMMARY] Summary generation completed for conflict {}", conflictId)
    }
}
