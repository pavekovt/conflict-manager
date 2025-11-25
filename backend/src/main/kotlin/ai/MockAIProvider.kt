package me.pavekovt.ai

import me.pavekovt.dto.FeelingsProcessingResult
import me.pavekovt.dto.NoteDTO

/**
 * Mock AI provider for development/testing.
 * Simulates psychotherapist responses with basic keyword detection.
 */
class MockAIProvider : AIProvider {

    override suspend fun processFeelingsAndSuggestResolution(
        userFeelings: String,
        userProfile: UserProfile,
        partnerProfile: UserProfile,
        partnershipContext: String?,
        previousFeelings: List<String>?,
        detectedLanguage: String
    ): FeelingsProcessingResult {
        // Mock: Detect emotional tone from keywords
        val emotionalTone = when {
            userFeelings.contains("angry", ignoreCase = true) ||
            userFeelings.contains("furious", ignoreCase = true) -> "angry"
            userFeelings.contains("hurt", ignoreCase = true) ||
            userFeelings.contains("sad", ignoreCase = true) -> "hurt"
            userFeelings.contains("frustrated", ignoreCase = true) ||
            userFeelings.contains("annoyed", ignoreCase = true) -> "frustrated"
            userFeelings.contains("worried", ignoreCase = true) ||
            userFeelings.contains("anxious", ignoreCase = true) -> "concerned"
            else -> "neutral"
        }

        // Generate personalized empathetic guidance
        val previousFeelingsNote = if (!previousFeelings.isNullOrEmpty()) {
            "\n\nI see you've shared feelings about this before. It's important that we keep working through this together."
        } else ""

        val guidance = when (emotionalTone) {
            "angry" -> """
                ${userProfile.name}, I hear that you're feeling angry about this situation with ${partnerProfile.name}. That's completely valid - anger often signals that an important boundary or value has been crossed.

                Before moving forward, take a moment to breathe. What's underneath the anger? Often it's hurt, fear, or feeling unheard. Understanding this can help you communicate more effectively with ${partnerProfile.name}.

                When you're ready, try expressing your needs without blame: "I need..." rather than "You always..."$previousFeelingsNote
            """.trimIndent()

            "hurt" -> """
                ${userProfile.name}, I can see this situation with ${partnerProfile.name} has hurt you. Your feelings are valid and deserve to be acknowledged.

                Hurt often comes from unmet expectations or feeling disconnected. As you process this, consider: What did you hope would happen? What need wasn't met?

                When sharing with ${partnerProfile.name}, vulnerability can be powerful. "I felt hurt when..." can open the door to deeper understanding.$previousFeelingsNote
            """.trimIndent()

            "frustrated" -> """
                ${userProfile.name}, frustration is understandable - it often arises when things aren't working as expected or when communication with ${partnerProfile.name} feels stuck.

                This is actually a good sign that you care about resolving this. The key is channeling that energy constructively.

                Try to identify the specific pattern or behavior that's frustrating you, rather than focusing on ${partnerProfile.name}'s character. This makes it easier to find solutions.$previousFeelingsNote
            """.trimIndent()

            else -> """
                ${userProfile.name}, thank you for sharing your perspective on this conflict with ${partnerProfile.name}. Taking time to express your feelings is an important first step toward resolution.

                As you think through this situation, consider:
                - What's most important to you here?
                - What would a good resolution look like?
                - What do you need from ${partnerProfile.name} to move forward?$previousFeelingsNote
            """.trimIndent()
        }

        // Generate suggested resolution
        val keywords = userFeelings.lowercase().split(" ")
            .filter { it.length > 4 }
            .distinct()
            .take(3)
            .joinToString(", ")

        val suggestedResolution = """
            Based on your feelings, ${userProfile.name}, here's a suggested approach for talking with ${partnerProfile.name}:

            1. Express your core need: Start with "I need..." to communicate what's most important to you
            2. Acknowledge ${partnerProfile.name}'s perspective: Even in conflict, try to understand their view
            3. Propose a specific change: What one thing could improve this situation?

            Example:
            "${partnerProfile.name}, I need us to communicate more clearly about ${keywords.ifEmpty { "this issue" }}. I understand we may see things differently, but I'd like us to [specific action]. Could we agree to [concrete step]?"
        """.trimIndent()

        return FeelingsProcessingResult(
            guidance = guidance,
            suggestedResolution = suggestedResolution,
            emotionalTone = emotionalTone
        )
    }

    override suspend fun summarizeConflict(
        resolution1: String,
        resolution2: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile,
        partnershipContext: String?,
        detectedLanguage: String
    ): SummaryResult {
        val contextNote = if (partnershipContext != null) {
            " (building on your ${partnershipContext.length} characters of relationship history)"
        } else ""

        val summary = """
            Based on what ${user1Profile.name} and ${user2Profile.name} have shared, you've decided to work together on this issue.

            ${user1Profile.name} emphasized: "${resolution1.take(80)}${if (resolution1.length > 80) "..." else ""}"
            ${user2Profile.name} emphasized: "${resolution2.take(80)}${if (resolution2.length > 80) "..." else ""}"

            Moving forward, you'll both commit to this resolution$contextNote.
        """.trimIndent()

        // Generate mock patterns
        val patterns = if (partnershipContext != null) {
            "I'm noticing that ${user1Profile.name} and ${user2Profile.name} value clear communication. This conflict shows similar themes to past discussions about expectations and boundaries. Your willingness to work through this together is a strength."
        } else {
            "This is your first session together. As we continue working, patterns will emerge that help me provide better guidance tailored to your relationship."
        }

        // Generate therapeutic advice
        val advice = """
            Here's what I recommend for ${user1Profile.name} and ${user2Profile.name}:
            1. Schedule a follow-up check-in in one week to ensure this resolution is working for both of you
            2. Create a shared note about expectations to prevent similar conflicts from arising
            3. Celebrate small wins when you successfully navigate disagreements - this builds resilience
        """.trimIndent()

        // Detect recurring issues (mock)
        val recurringIssues = if (partnershipContext != null && partnershipContext.contains("communication")) {
            listOf("Communication expectations", "Response time concerns")
        } else {
            emptyList()
        }

        // Suggest theme tags
        val themeTags = listOf("communication", "expectations", "boundaries", "quality_time", "appreciation")
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

    override suspend fun updatePartnershipContextWithConflict(
        existingContext: String?,
        conflictSummary: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile
    ): String {
        val contextBuilder = StringBuilder()

        // Include existing context
        if (existingContext != null) {
            contextBuilder.append(existingContext)
            contextBuilder.append("\n\n---\n\n")
        } else {
            contextBuilder.append("SESSION NOTES for ${user1Profile.name} & ${user2Profile.name}\n")
            contextBuilder.append("${user1Profile.name}: ${user1Profile.toContextString()}\n")
            contextBuilder.append("${user2Profile.name}: ${user2Profile.toContextString()}\n\n")
        }

        // Add conflict resolution
        contextBuilder.append("Recent conflict resolved: $conflictSummary ")

        // Trim to reasonable size
        val result = contextBuilder.toString()
        return if (result.length > 2000) {
            result.takeLast(2000)
        } else {
            result
        }
    }

    override suspend fun updatePartnershipContextWithRetrospective(
        existingContext: String?,
        retroSummary: String,
        retroNotes: List<String>,
        approvalText1: String?,
        approvalText2: String?
    ): String {
        val contextBuilder = StringBuilder()

        // Include existing context
        if (existingContext != null) {
            contextBuilder.append(existingContext)
            contextBuilder.append("\n\n---\n\n")
        }

        // Add retrospective summary
        contextBuilder.append("Retrospective completed: $retroSummary\n")
        contextBuilder.append("Key topics discussed: ${retroNotes.take(3).joinToString(", ") { it.take(50) }}\n")

        // Include approval texts if provided
        if (approvalText1 != null || approvalText2 != null) {
            contextBuilder.append("Partners' agreement perspectives:\n")
            if (approvalText1 != null) {
                contextBuilder.append("  Partner 1: ${approvalText1.take(100)}\n")
            }
            if (approvalText2 != null) {
                contextBuilder.append("  Partner 2: ${approvalText2.take(100)}\n")
            }
        }

        // Trim to reasonable size
        val result = contextBuilder.toString()
        return if (result.length > 2000) {
            result.takeLast(2000)
        } else {
            result
        }
    }

    override fun detectLanguage(text: String): String {
        // Simple mock detection - matches basic patterns
        return when {
            text.matches(Regex(".*[А-Яа-яЁё].*")) -> "ru"
            text.matches(Regex(".*[ÁáÉéÍíÓóÚúÑñ].*")) -> "es"
            text.matches(Regex(".*[ÀàÂâÇçÈèÉéÊêËë].*")) -> "fr"
            text.matches(Regex(".*[ÄäÖöÜüß].*")) -> "de"
            else -> "en"
        }
    }

    private fun extractKeywords(text: String): String {
        val keywords = text.lowercase()
            .split(" ")
            .filter { it.length > 5 }
            .distinct()
            .take(5)
            .joinToString(", ")

        return keywords.ifEmpty { "general topics" }
    }
}
