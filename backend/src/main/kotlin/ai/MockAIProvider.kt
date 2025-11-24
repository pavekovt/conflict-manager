package me.pavekovt.ai

import me.pavekovt.dto.NoteDTO

/**
 * Mock AI provider for development/testing.
 * Replace with real Claude or OpenAI implementation in production.
 */
class MockAIProvider : AIProvider {

    override suspend fun summarizeConflict(
        resolution1: String,
        resolution2: String,
        partnershipContext: String?
    ): SummaryResult {
        // Simple mock: combine both resolutions
        val contextNote = if (partnershipContext != null) {
            "\n\n[Using historical context from previous ${partnershipContext.length} chars of relationship history]"
        } else ""

        val summary = """
            We decided that both perspectives are valid.

            Partner 1 mentioned: "${resolution1.take(100)}${if (resolution1.length > 100) "..." else ""}"
            Partner 2 mentioned: "${resolution2.take(100)}${if (resolution2.length > 100) "..." else ""}"

            Moving forward, we'll work together on this issue.$contextNote
        """.trimIndent()

        // Generate mock patterns based on context
        val patterns = if (partnershipContext != null) {
            "Based on your relationship history, you both value clear communication. This conflict shows similar themes to past discussions about expectations and boundaries."
        } else {
            "This is your first recorded conflict. As you continue working together, patterns will emerge that help us provide better guidance."
        }

        // Generate mock advice
        val advice = """
            1. Schedule a follow-up check-in in one week to ensure the resolution is working
            2. Consider creating a shared note about expectations to prevent similar conflicts
            3. Celebrate small wins when you successfully navigate disagreements
        """.trimIndent()

        // Detect recurring issues (mock)
        val recurringIssues = if (partnershipContext != null && partnershipContext.contains("communication")) {
            listOf("Communication expectations", "Response time concerns")
        } else {
            emptyList()
        }

        // Suggest theme tags (mock)
        val themeTags = listOf("communication", "expectations", "boundaries")
            .filter { theme ->
                resolution1.contains(theme, ignoreCase = true) ||
                resolution2.contains(theme, ignoreCase = true)
            }

        return SummaryResult(
            summary = summary,
            patterns = patterns,
            advice = advice,
            recurringIssues = recurringIssues,
            themeTags = if (themeTags.isEmpty()) listOf("general") else themeTags,
            provider = "mock"
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

    override suspend fun updatePartnershipContext(
        existingContext: String?,
        newConflictSummary: String?,
        newResolutions: Pair<String, String>?,
        retroSummary: String?,
        retroNotes: List<String>?
    ): String {
        // Mock implementation: create a compacted summary
        val contextBuilder = StringBuilder()

        // Include existing context
        if (existingContext != null) {
            contextBuilder.append(existingContext)
            contextBuilder.append("\n\n---\n\n")
        } else {
            contextBuilder.append("RELATIONSHIP CONTEXT SUMMARY\n\n")
        }

        // Add conflict info if provided
        if (newConflictSummary != null && newResolutions != null) {
            contextBuilder.append("Recent Conflict: Both partners discussed ${extractKeywords(newResolutions.first + " " + newResolutions.second)}. ")
            contextBuilder.append("Resolution: $newConflictSummary. ")
        }

        // Add retro info if provided
        if (retroSummary != null) {
            contextBuilder.append("Retrospective completed: $retroSummary. ")
            if (retroNotes != null && retroNotes.isNotEmpty()) {
                contextBuilder.append("Topics covered: ${extractKeywords(retroNotes.joinToString(" "))}. ")
            }
        }

        // Trim to reasonable size (in real implementation, AI would intelligently compress)
        val result = contextBuilder.toString()
        return if (result.length > 2000) {
            // Keep most recent 2000 chars for simplicity in mock
            result.takeLast(2000)
        } else {
            result
        }
    }

    private fun extractKeywords(text: String): String {
        // Simple mock keyword extraction
        val keywords = text.lowercase()
            .split(" ")
            .filter { it.length > 5 }
            .distinct()
            .take(5)
            .joinToString(", ")

        return keywords.ifEmpty { "general topics" }
    }
}
