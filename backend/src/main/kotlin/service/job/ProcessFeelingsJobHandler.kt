package me.pavekovt.service.job

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.pavekovt.ai.AIProvider
import me.pavekovt.entity.ConflictFeelingsStatus
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.repository.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Handles PROCESS_FEELINGS job type
 */
class ProcessFeelingsJobHandler(
    private val conflictFeelingsRepository: ConflictFeelingsRepository,
    private val conflictRepository: ConflictRepository,
    private val userRepository: UserRepository,
    private val partnershipRepository: PartnershipRepository,
    private val aiProvider: AIProvider,
    private val userProfileLoader: UserProfileLoader,
    private val contextLoader: PartnershipContextLoader
) {
    private val logger = LoggerFactory.getLogger(ProcessFeelingsJobHandler::class.java)

    suspend fun process(feelingId: UUID, payload: String?) {
        logger.info("[FEELINGS] Starting processing for feeling {}", feelingId)

        // Load feeling
        val feeling = conflictFeelingsRepository.findById(feelingId)
            ?: throw IllegalStateException("Feeling $feelingId not found")

        val userId = UUID.fromString(feeling.userId)
        val conflictId = UUID.fromString(feeling.conflictId)

        logger.debug("[FEELINGS] Feeling from user {}, conflict {}", userId, conflictId)

        // Load conflict
        val conflict = conflictRepository.findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict $conflictId not found")

        // Load previous feelings for context
        val previousFeelings = conflictFeelingsRepository
            .findByConflictAndUser(conflictId, userId)
            .filter { it.status == ConflictFeelingsStatus.COMPLETED }

        logger.debug("[FEELINGS] Found {} previous completed feelings", previousFeelings.size)

        // Extract partnership context from payload
        val partnershipContext = extractPartnershipContext(payload)
            ?: contextLoader.loadContext(userId, processJournals = true)

        // Detect language
        val detectedLanguage = aiProvider.detectLanguage(feeling.feelingsText)
        logger.debug("[FEELINGS] Detected language: {}", detectedLanguage)

        // Load user and partner profiles
        val partnerId = findPartnerId(userId, conflict.initiatedBy)
        val (userProfile, partnerProfile) = userProfileLoader.loadProfiles(userId, partnerId)

        logger.info("[FEELINGS] Calling AI provider for feelings processing")

        // Call AI
        val aiResponse = aiProvider.processFeelingsAndSuggestResolution(
            userFeelings = feeling.feelingsText,
            userProfile = userProfile,
            partnerProfile = partnerProfile,
            partnershipContext = partnershipContext,
            previousFeelings = previousFeelings.map { it.feelingsText },
            detectedLanguage = detectedLanguage
        )

        logger.info("[FEELINGS] AI processing completed, updating feeling record")

        // Update feeling with AI response
        conflictFeelingsRepository.updateWithAIResponse(
            feelingId = feelingId,
            aiGuidance = aiResponse.guidance,
            suggestedResolution = aiResponse.suggestedResolution,
            emotionalTone = aiResponse.emotionalTone
        )

        // Check if we should move to PENDING_RESOLUTIONS
        val completedCount = conflictFeelingsRepository.countCompletedFeelings(conflictId)
        if (completedCount >= 2) {
            logger.info("[FEELINGS] Both partners have submitted feelings, moving conflict to PENDING_RESOLUTIONS")
            conflictRepository.updateStatus(conflictId, ConflictStatus.PENDING_RESOLUTIONS)
        }

        logger.info("[FEELINGS] Processing completed for feeling {}", feelingId)
    }

    private fun extractPartnershipContext(payload: String?): String? {
        return payload?.let {
            try {
                Json.parseToJsonElement(it).jsonObject["partnershipContext"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                logger.warn("[FEELINGS] Failed to parse partnership context from payload", e)
                null
            }
        }
    }

    private suspend fun findPartnerId(userId: UUID, conflictInitiatorId: String): UUID {
        val initiatorId = UUID.fromString(conflictInitiatorId)

        return if (initiatorId == userId) {
            // User is initiator, find partner
            val partnership = partnershipRepository.findActivePartnership(userId)
            UUID.fromString(partnership?.partnerId
                ?: throw IllegalStateException("No active partnership for user $userId"))
        } else {
            // User is not initiator, so initiator is the partner
            initiatorId
        }
    }
}
