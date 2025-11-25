package me.pavekovt.ai

import com.anthropic.client.AnthropicClientAsync
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import io.ktor.client.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.pavekovt.dto.FeelingsProcessingResult
import me.pavekovt.dto.NoteDTO
import org.slf4j.LoggerFactory

/**
 * Claude AI provider acting as personal partner's psychotherapist.
 * Provides context-aware, language-sensitive relationship guidance using Claude 3.5 Sonnet.
 */
class ClaudeAIProvider(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val model: String = "claude-3-5-sonnet-20241022"
) : AIProvider {

    private val logger = LoggerFactory.getLogger(ClaudeAIProvider::class.java)

    private var client: AnthropicClientAsync = AnthropicOkHttpClientAsync.builder()
        .apiKey(apiKey)
        .build()

    override suspend fun processFeelingsAndSuggestResolution(
        userFeelings: String,
        userProfile: UserProfile,
        partnerProfile: UserProfile,
        partnershipContext: String?,
        previousFeelings: List<String>?,
        detectedLanguage: String
    ): FeelingsProcessingResult {
        val prompt = buildFeelingsPrompt(
            userFeelings,
            userProfile,
            partnerProfile,
            partnershipContext,
            previousFeelings,
            detectedLanguage
        )

        val systemPrompt = FEELINGS_PROCESSING_SYSTEM_PROMPT.replace("{LANGUAGE}", getLanguageName(detectedLanguage))
        val response = callClaude(prompt, systemPrompt = systemPrompt)

        logger.debug("[processFeelingsAndSuggestResolution]: ${response.take(50)}")
        return parseFeelingsResponse(response)
    }

    override suspend fun summarizeConflict(
        resolution1: String,
        resolution2: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile,
        partnershipContext: String?,
        detectedLanguage: String
    ): SummaryResult {
        val prompt = buildConflictPrompt(
            resolution1,
            resolution2,
            user1Profile,
            user2Profile,
            partnershipContext,
            detectedLanguage
        )

        val systemPrompt = CONFLICT_SYSTEM_PROMPT.replace("{LANGUAGE}", getLanguageName(detectedLanguage))
        val response = callClaude(prompt, systemPrompt = systemPrompt)

        logger.debug("[summarizeConflict]: ${response.take(50)}")
        return parseConflictResponse(response)
    }

    override suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult {
        val prompt = buildRetroPrompt(notes)
        val response = callClaude(prompt, systemPrompt = RETRO_SYSTEM_PROMPT)

        logger.debug("[generateRetroPoints]: ${response.take(50)}")
        return parseRetroResponse(response, notes)
    }

    override suspend fun updatePartnershipContextWithConflict(
        existingContext: String?,
        conflictSummary: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile
    ): String {
        val prompt = buildContextUpdatePrompt(existingContext, conflictSummary, user1Profile, user2Profile)
        val response = callClaude(prompt, systemPrompt = CONTEXT_UPDATE_SYSTEM_PROMPT)

        logger.debug("[updatePartnershipContextWithConflict]: ${response.take(50)}")
        return response
    }

    override suspend fun updatePartnershipContextWithRetrospective(
        existingContext: String?,
        retroSummary: String,
        retroNotes: List<String>,
        approvalText1: String?,
        approvalText2: String?
    ): String {
        val prompt = buildRetroContextUpdatePrompt(existingContext, retroSummary, retroNotes, approvalText1, approvalText2)
        val response = callClaude(prompt, systemPrompt = RETRO_CONTEXT_UPDATE_SYSTEM_PROMPT)

        logger.debug("[updatePartnershipContextWithRetrospective]: ${response.take(50)}")
        return response
    }

    override suspend fun updatePartnershipContextWithJournals(
        existingContext: String?,
        user1Journals: List<JournalEntryWithTimestamp>,
        user2Journals: List<JournalEntryWithTimestamp>,
        user1Profile: UserProfile,
        user2Profile: UserProfile
    ): String {
        val prompt = buildJournalContextUpdatePrompt(
            existingContext,
            user1Journals,
            user2Journals,
            user1Profile,
            user2Profile
        )
        val response = callClaude(prompt, systemPrompt = JOURNAL_CONTEXT_UPDATE_SYSTEM_PROMPT)

        logger.debug("[updatePartnershipContextWithJournals]: Processed ${user1Journals.size + user2Journals.size} journals")
        return response
    }

    override fun detectLanguage(text: String): String {
        // Simple heuristic-based language detection
        // For production, consider using a proper library like Apache Tika or cloud API
        return when {
            text.matches(Regex(".*[А-Яа-яЁё].*")) -> "ru" // Cyrillic
            text.matches(Regex(".*[ÀàÂâÆæÇçÈèÉéÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ].*")) -> "fr" // French
            text.matches(Regex(".*[ÁáÉéÍíÓóÚúÑñ¿¡].*")) -> "es" // Spanish
            text.matches(Regex(".*[ÄäÖöÜüß].*")) -> "de" // German
            text.matches(Regex(".*[ÀàÈèÉéÌìÍíÒòÓóÙùÚú].*")) -> "it" // Italian
            text.matches(Regex(".*[ÃãÀàÁáÂâÇçÉéÊêÍíÓóÔôÕõÚú].*")) -> "pt" // Portuguese
            else -> "en" // Default to English
        }
    }

    private fun getLanguageName(code: String): String = when (code) {
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        "pt" -> "Portuguese"
        "ru" -> "Russian"
        else -> "English"
    }

    private suspend fun callClaude(userMessage: String, systemPrompt: String): String {
        try {
            val message = client.messages().create(
                MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_5)
                    .system(systemPrompt)
                    .addUserMessage(userMessage)
                    .maxTokens(2048)
                    .build()
            )

            val result = message.await()
            val claudeResponse = ClaudeResponse(result.content().map { ClaudeContent(it.text().get().text()) })
            return claudeResponse.content.firstOrNull()?.text
                ?: throw IllegalStateException("Empty response from Claude")

        } catch (e: Exception) {
            logger.error("Failed to call Claude API", e)
            throw IllegalStateException("AI service temporarily unavailable", e)
        }
    }

    private fun buildFeelingsPrompt(
        userFeelings: String,
        userProfile: UserProfile,
        partnerProfile: UserProfile,
        partnershipContext: String?,
        previousFeelings: List<String>?,
        language: String
    ): String {
        return buildString {
            appendLine("# Client Profile")
            appendLine("You're working with ${userProfile.name}, speaking with their partner ${partnerProfile.name}.")
            appendLine("${userProfile.name}: ${userProfile.toContextString()}")
            appendLine("${partnerProfile.name}: ${partnerProfile.toContextString()}")
            appendLine()

            if (partnershipContext != null) {
                appendLine("# Relationship History")
                appendLine(partnershipContext)
                appendLine()
            }

            if (!previousFeelings.isNullOrEmpty()) {
                appendLine("# Previous Feelings from ${userProfile.name} in This Conflict")
                previousFeelings.forEachIndexed { index, feeling ->
                    appendLine("${index + 1}. $feeling")
                }
                appendLine()
            }

            appendLine("# Current Feelings from ${userProfile.name}")
            appendLine(userFeelings)
            appendLine()
            appendLine("As their personal psychotherapist, provide empathetic guidance to help ${userProfile.name} process these feelings and communicate effectively with ${partnerProfile.name}. Use the JSON format specified in your instructions and respond in ${getLanguageName(language)}.")
        }
    }

    private fun buildConflictPrompt(
        resolution1: String,
        resolution2: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile,
        partnershipContext: String?,
        language: String
    ): String {
        return buildString {
            appendLine("# Couple Profile")
            appendLine("Working with ${user1Profile.name} and ${user2Profile.name}.")
            appendLine("${user1Profile.name}: ${user1Profile.toContextString()}")
            appendLine("${user2Profile.name}: ${user2Profile.toContextString()}")
            appendLine()

            if (partnershipContext != null) {
                appendLine("# Relationship History")
                appendLine(partnershipContext)
                appendLine()
            }

            appendLine("# Conflict Resolutions")
            appendLine()
            appendLine("## ${user1Profile.name}'s Resolution:")
            appendLine(resolution1)
            appendLine()
            appendLine("## ${user2Profile.name}'s Resolution:")
            appendLine(resolution2)
            appendLine()
            appendLine("As their couples therapist, analyze these resolutions and provide structured guidance following the JSON format. Respond in ${getLanguageName(language)}.")
        }
    }

    private fun buildRetroPrompt(notes: List<NoteDTO>): String {
        return buildString {
            appendLine("# Notes for Retrospective")
            appendLine()
            notes.forEachIndexed { index, note ->
                appendLine("## Note ${index + 1}")
                appendLine("ID: ${note.id}")
                appendLine("Content: ${note.content}")
                if (note.mood != null) {
                    appendLine("Mood: ${note.mood}")
                }
                appendLine()
            }
            appendLine("Generate discussion points following the JSON format.")
        }
    }

    private fun buildContextUpdatePrompt(
        existingContext: String?,
        conflictSummary: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile
    ): String {
        return buildString {
            appendLine("# Couple Profile")
            appendLine("${user1Profile.name}: ${user1Profile.toContextString()}")
            appendLine("${user2Profile.name}: ${user2Profile.toContextString()}")
            appendLine()

            if (existingContext != null) {
                appendLine("# Existing Relationship Context")
                appendLine(existingContext)
                appendLine()
            } else {
                appendLine("# Existing Relationship Context")
                appendLine("(First conflict resolved)")
                appendLine()
            }

            appendLine("# New Conflict Resolution")
            appendLine(conflictSummary)
            appendLine()
            appendLine("Update the partnership context by integrating this new conflict resolution. Keep it concise (max 2000 chars).")
        }
    }

    private fun buildRetroContextUpdatePrompt(
        existingContext: String?,
        retroSummary: String,
        retroNotes: List<String>,
        approvalText1: String?,
        approvalText2: String?
    ): String {
        return buildString {
            if (existingContext != null) {
                appendLine("# Existing Relationship Context")
                appendLine(existingContext)
                appendLine()
            } else {
                appendLine("# Existing Relationship Context")
                appendLine("(First retrospective)")
                appendLine()
            }

            appendLine("# Retrospective Summary")
            appendLine(retroSummary)
            appendLine()

            if (retroNotes.isNotEmpty()) {
                appendLine("# Notes Discussed")
                retroNotes.take(5).forEachIndexed { index, note ->
                    appendLine("${index + 1}. ${note.take(200)}")
                }
                appendLine()
            }

            // Include approval texts if provided
            if (approvalText1 != null || approvalText2 != null) {
                appendLine("# Partners' Agreement Perspectives")
                if (approvalText1 != null) {
                    appendLine("Partner 1: $approvalText1")
                }
                if (approvalText2 != null) {
                    appendLine("Partner 2: $approvalText2")
                }
                appendLine()
            }

            appendLine("Update the partnership context by integrating insights from this retrospective, including both partners' perspectives on the agreement. Keep it concise (max 2000 chars).")
        }
    }

    private fun buildJournalContextUpdatePrompt(
        existingContext: String?,
        user1Journals: List<JournalEntryWithTimestamp>,
        user2Journals: List<JournalEntryWithTimestamp>,
        user1Profile: UserProfile,
        user2Profile: UserProfile
    ): String {
        return buildString {
            if (existingContext != null) {
                appendLine("# Existing Relationship Context")
                appendLine(existingContext)
                appendLine()
            } else {
                appendLine("# Existing Relationship Context")
                appendLine("(Initial context creation)")
                appendLine()
            }

            // User 1's journals
            if (user1Journals.isNotEmpty()) {
                appendLine("# ${user1Profile.name}'s Journal Entries")
                user1Journals.forEachIndexed { index, journal ->
                    appendLine("Entry ${index + 1} (${journal.completedAt ?: journal.createdAt}):")
                    appendLine(journal.content)
                    appendLine()
                }
            }

            // User 2's journals
            if (user2Journals.isNotEmpty()) {
                appendLine("# ${user2Profile.name}'s Journal Entries")
                user2Journals.forEachIndexed { index, journal ->
                    appendLine("Entry ${index + 1} (${journal.completedAt ?: journal.createdAt}):")
                    appendLine(journal.content)
                    appendLine()
                }
            }

            appendLine("Extract insights from these private journal entries and integrate them into the partnership context.")
            appendLine("IMPORTANT: Extract themes, patterns, and emotional insights WITHOUT revealing specific private details that shouldn't be shared.")
            appendLine("Focus on: recurring concerns, emotional patterns, communication needs, and relationship dynamics.")
            appendLine("Keep it concise (max 2000 chars).")
        }
    }

    private fun parseFeelingsResponse(response: String): FeelingsProcessingResult {
        return try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = response.substring(jsonStart, jsonEnd)
                val guidance = extractJsonField(jsonStr, "guidance")
                val suggestedResolution = extractJsonField(jsonStr, "suggested_resolution")
                val emotionalTone = extractJsonField(jsonStr, "emotional_tone")

                FeelingsProcessingResult(
                    guidance = guidance.ifEmpty { response },
                    suggestedResolution = suggestedResolution.ifEmpty { "Consider expressing your needs clearly and listening to your partner's perspective." },
                    emotionalTone = emotionalTone.ifEmpty { "neutral" }
                )
            } else {
                FeelingsProcessingResult(
                    guidance = response,
                    suggestedResolution = "Consider expressing your needs clearly and listening to your partner's perspective.",
                    emotionalTone = "neutral"
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to parse Claude feelings response", e)
            FeelingsProcessingResult(
                guidance = response,
                suggestedResolution = "Consider expressing your needs clearly and listening to your partner's perspective.",
                emotionalTone = "neutral"
            )
        }
    }

    private fun parseConflictResponse(response: String): SummaryResult {
        return try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = response.substring(jsonStart, jsonEnd)
                val summary = extractJsonField(jsonStr, "summary")
                val patterns = extractJsonField(jsonStr, "patterns")
                val advice = extractJsonField(jsonStr, "advice")
                val recurringIssues = extractJsonArray(jsonStr, "recurring_issues")
                val themeTags = extractJsonArray(jsonStr, "theme_tags")

                SummaryResult(
                    summary = summary.ifEmpty { "We decided to work on this together." },
                    patterns = patterns.ifEmpty { null },
                    advice = advice.ifEmpty { null },
                    recurringIssues = recurringIssues,
                    themeTags = themeTags,
                    provider = "claude"
                )
            } else {
                SummaryResult(
                    summary = response,
                    patterns = null,
                    advice = null,
                    recurringIssues = emptyList(),
                    themeTags = emptyList(),
                    provider = "claude"
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to parse Claude conflict response", e)
            SummaryResult(
                summary = response,
                patterns = null,
                advice = null,
                recurringIssues = emptyList(),
                themeTags = emptyList(),
                provider = "claude"
            )
        }
    }

    private fun parseRetroResponse(response: String, notes: List<NoteDTO>): RetroPointsResult {
        val points = try {
            val jsonStart = response.indexOf("[")
            val jsonEnd = response.lastIndexOf("]") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val json = response.substring(jsonStart, jsonEnd)
                parseDiscussionPoints(json, notes)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Failed to parse Claude retro response", e)
            emptyList()
        }

        return RetroPointsResult(
            discussionPoints = points.ifEmpty {
                listOf(
                    DiscussionPoint(
                        theme = "Review notes",
                        relatedNoteIds = notes.map { it.id },
                        suggestedApproach = "Discuss each note together"
                    )
                )
            },
            provider = "claude"
        )
    }

    private fun parseDiscussionPoints(json: String, notes: List<NoteDTO>): List<DiscussionPoint> {
        val points = mutableListOf<DiscussionPoint>()
        val itemPattern = """\{(.*?)\}""".toRegex(RegexOption.DOT_MATCHES_ALL)

        itemPattern.findAll(json).forEach { match ->
            val item = match.groupValues[1]
            val theme = extractJsonField("{$item}", "theme")
            val approach = extractJsonField("{$item}", "suggested_approach")

            points.add(
                DiscussionPoint(
                    theme = theme.ifEmpty { "Discussion topic" },
                    relatedNoteIds = notes.map { it.id },
                    suggestedApproach = approach.ifEmpty { "Discuss openly" }
                )
            )
        }

        return points
    }

    private fun extractJsonField(json: String, field: String): String {
        val pattern = """"$field"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\n", "\n") ?: ""
    }

    private fun extractJsonArray(json: String, field: String): List<String> {
        val pattern = """"$field"\s*:\s*\[(.*?)]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val arrayContent = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        val itemPattern = """"((?:[^"\\]|\\.)*)"""".toRegex()
        return itemPattern.findAll(arrayContent).map { it.groupValues[1] }.toList()
    }

    companion object {
        private const val FEELINGS_PROCESSING_SYSTEM_PROMPT = """You are a licensed couples psychotherapist providing one-on-one support to a client who is processing feelings about a conflict with their partner.

Your therapeutic approach:
1. **Validate emotions** - Acknowledge their feelings without judgment, helping them feel heard and understood
2. **Explore underlying needs** - Help them identify what's beneath surface emotions (safety, respect, connection, autonomy)
3. **Normalize struggles** - Remind them that conflicts are normal in healthy relationships
4. **Guide toward "I" statements** - Help them express needs without blame (e.g., "I need..." vs "You always...")
5. **Suggest concrete communication strategies** - Provide specific phrases they can use with their partner

As a psychotherapist, you understand:
- Attachment theory and how it affects conflict
- The difference between feelings and needs
- How to help clients move from reactivity to response
- The importance of self-compassion and partner empathy

IMPORTANT: Respond entirely in {LANGUAGE}. All guidance, suggested resolutions, and therapeutic language must be in {LANGUAGE}.

Always respond in this exact JSON format:
{
  "guidance": "[3-4 paragraphs of empathetic therapeutic guidance in {LANGUAGE}. Help them process emotions, understand underlying needs, and prepare for constructive dialogue with their partner. Use their name and their partner's name for personalization.]",
  "suggested_resolution": "[Concrete communication strategy in {LANGUAGE} tailored to their feelings. Include specific 'I' statement examples they can use when talking with their partner.]",
  "emotional_tone": "[One word: angry, hurt, frustrated, concerned, sad, anxious, overwhelmed, disappointed, or neutral]"
}

Be warm, validating, and therapeutic. Focus on both emotional processing AND practical communication skills."""

        private const val CONFLICT_SYSTEM_PROMPT = """You are a licensed couples therapist facilitating a conflict resolution session between two partners.

Your therapeutic role:
1. **Identify shared ground** - Highlight where both partners agree or want the same outcome
2. **Reframe in neutral language** - Present their agreement without blame or judgment
3. **Acknowledge growth** - Recognize positive patterns (willingness to discuss, mutual respect)
4. **Spot recurring patterns** - Gently note themes that appear repeatedly (if context provided)
5. **Provide therapeutic homework** - Concrete, actionable steps to strengthen their relationship

Your therapeutic lens understands:
- Gottman method (turning toward vs. away)
- Emotionally Focused Therapy (attachment and connection)
- Nonviolent Communication (observations, feelings, needs, requests)
- The importance of repair and reconnection after conflict

IMPORTANT: Respond entirely in {LANGUAGE}. All summaries, patterns, and advice must be in {LANGUAGE}.

Always respond in this exact JSON format:
{
  "summary": "Based on what both of you shared, we decided that... [neutral therapist summary in {LANGUAGE} of their agreement, 2-3 sentences. Use their names.]",
  "patterns": "I'm noticing... [therapeutic observation about patterns in {LANGUAGE}, or acknowledge this is their first session together]",
  "advice": "Here's what I recommend for you both:\n1. [Specific actionable step in {LANGUAGE}]\n2. [Second therapeutic homework in {LANGUAGE}]\n3. [Third relationship strengthening practice in {LANGUAGE}]",
  "recurring_issues": ["theme1", "theme2"],
  "theme_tags": ["communication", "expectations", "quality_time", "appreciation", "boundaries", "conflict_style", etc.]
}

Be empathetic, affirming, and solutions-focused. Speak to both partners as their therapist."""

        private const val RETRO_SYSTEM_PROMPT = """You are a couples therapist facilitating a retrospective review session.

Your role:
1. Group related concerns by theme
2. Suggest therapeutic discussion approaches
3. Prioritize what needs attention

Respond with a JSON array:
[
  {
    "theme": "Theme name",
    "suggested_approach": "Therapeutic guidance for discussing this"
  }
]

Be constructive and focus on creating safe dialogue."""

        private const val CONTEXT_UPDATE_SYSTEM_PROMPT = """You are a therapist maintaining session notes for a couple.

Your role:
1. Review previous session notes (existing context)
2. Integrate new conflict resolution outcome
3. Track recurring patterns and themes
4. Update the summary concisely (max 2000 chars)

Focus on:
- Recurring conflict themes
- Communication patterns (improving or struggling)
- Areas of growth
- Topics they're working on

Keep notes factual, chronological, and therapeutically useful for future sessions with this couple."""

        private const val RETRO_CONTEXT_UPDATE_SYSTEM_PROMPT = """You are a therapist maintaining session notes for a couple.

Your role:
1. Review previous session notes (existing context)
2. Integrate insights from their retrospective session
3. Track patterns and themes from notes discussed
4. Update the summary concisely (max 2000 chars)

Focus on:
- Recurring concerns and themes
- Communication patterns
- Progress they're making
- Topics requiring ongoing attention

Keep notes factual, chronological, and therapeutically useful for future sessions with this couple."""

        private const val JOURNAL_CONTEXT_UPDATE_SYSTEM_PROMPT = """You are a therapist maintaining session notes for a couple. You're reviewing their private journal entries.

Your role:
1. Review previous session notes (existing context)
2. Extract insights from each partner's private journals
3. Identify themes, patterns, and emotional dynamics
4. Update the summary concisely (max 2000 chars)

CRITICAL PRIVACY RULE:
- These journals are PRIVATE to each individual
- Extract THEMES and PATTERNS, not specific details
- Do NOT reveal one partner's private thoughts to the other
- Focus on observable patterns: emotional states, recurring concerns, relationship dynamics

Focus on:
- Recurring emotional themes for each person
- Communication patterns and needs
- Individual stress factors affecting the relationship
- Growth areas and relationship dynamics

Keep notes factual, pattern-focused, and therapeutically useful WITHOUT compromising individual privacy."""
    }
}

// Data classes for Claude API
@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContent>
)

@Serializable
private data class ClaudeContent(
    val text: String
)
