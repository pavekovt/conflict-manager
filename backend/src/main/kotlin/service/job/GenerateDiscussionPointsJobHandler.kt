package me.pavekovt.service.job

import me.pavekovt.ai.AIProvider
import me.pavekovt.entity.RetroStatus
import me.pavekovt.repository.RetrospectiveRepository
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Handles GENERATE_DISCUSSION_POINTS job type
 */
class GenerateDiscussionPointsJobHandler(
    private val retrospectiveRepository: RetrospectiveRepository,
    private val aiProvider: AIProvider,
    private val contextLoader: PartnershipContextLoader
) {
    private val logger = LoggerFactory.getLogger(GenerateDiscussionPointsJobHandler::class.java)

    suspend fun process(retroId: UUID) {
        logger.info("[RETRO_POINTS] Starting discussion point generation for retro {}", retroId)

        // Load retrospective
        val retro = retrospectiveRepository.findById(retroId)
            ?: throw IllegalStateException("Retrospective $retroId not found")

        // Load notes
        val notes = retrospectiveRepository.getNotesForRetrospective(retroId)
        logger.debug("[RETRO_POINTS] Found {} notes for retrospective", notes.size)

        // Process journals before generating discussion points
        val retroUsers = retrospectiveRepository.getUsersForRetrospective(retroId)
        if (retroUsers.isNotEmpty()) {
            logger.debug("[RETRO_POINTS] Processing journals for {} users", retroUsers.size)
            val firstUserId = retroUsers[0]
            contextLoader.loadContext(firstUserId, processJournals = true)
        }

        logger.info("[RETRO_POINTS] Calling AI provider for discussion points generation")

        // Call AI to generate discussion points
        val discussionPointsResult = aiProvider.generateRetroPoints(notes)

        // Format discussion points as text
        val discussionPointsText = formatDiscussionPoints(discussionPointsResult.discussionPoints)

        logger.info("[RETRO_POINTS] Generated {} discussion points, saving to database",
            discussionPointsResult.discussionPoints.size)

        // Update retrospective
        retrospectiveRepository.updateDiscussionPoints(retroId, discussionPointsText)
        retrospectiveRepository.updateStatus(retroId, RetroStatus.IN_PROGRESS)

        logger.info("[RETRO_POINTS] Discussion point generation completed for retro {}", retroId)
    }

    private fun formatDiscussionPoints(points: List<me.pavekovt.ai.DiscussionPoint>): String {
        return points.joinToString("\n\n") { point ->
            """
            **${point.theme}**
            ${point.suggestedApproach}
            Related notes: ${point.relatedNoteIds.joinToString(", ")}
            """.trimIndent()
        }
    }
}
