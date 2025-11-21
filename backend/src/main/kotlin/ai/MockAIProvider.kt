package me.pavekovt.ai

import me.pavekovt.dto.NoteDTO

/**
 * Mock AI provider for development/testing.
 * Replace with real Claude or OpenAI implementation in production.
 */
class MockAIProvider : AIProvider {

    override suspend fun summarizeConflict(resolution1: String, resolution2: String): SummaryResult {
        // Simple mock: combine both resolutions
        val summary = """
            We decided that both perspectives are valid.

            Partner 1 mentioned: "${resolution1.take(100)}${if (resolution1.length > 100) "..." else ""}"
            Partner 2 mentioned: "${resolution2.take(100)}${if (resolution2.length > 100) "..." else ""}"

            Moving forward, we'll work together on this issue.
        """.trimIndent()

        val discrepancies = if (resolution1.lowercase() != resolution2.lowercase()) {
            listOf("Both partners described the resolution differently - please discuss to align")
        } else null

        return SummaryResult(
            summary = summary,
            provider = "mock",
            discrepancies = discrepancies
        )
    }

    override suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult {
        // Group notes by mood
        val grouped = notes.groupBy { it.mood ?: "neutral" }

        val discussionPoints = grouped.map { (mood, notesInMood) ->
            DiscussionPoint(
                theme = "Concerns related to $mood (${notesInMood.size} note${if (notesInMood.size > 1) "s" else ""})",
                relatedNoteIds = notesInMood.map { it.id },
                suggestedApproach = when (mood) {
                    "angry", "frustrated" -> "Address these issues with empathy and active listening"
                    "sad", "concerned" -> "Provide support and work together on solutions"
                    else -> "Discuss openly and find common ground"
                }
            )
        }

        return RetroPointsResult(
            discussionPoints = discussionPoints,
            provider = "mock"
        )
    }
}
