package me.pavekovt.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.pavekovt.dto.NoteDTO
import org.slf4j.LoggerFactory

/**
 * Claude AI provider using Anthropic Messages API.
 * Provides context-aware relationship advice using Claude 3.5 Sonnet.
 */
class ClaudeAIProvider(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val model: String = "claude-3-5-sonnet-20241022"
) : AIProvider {

    private val logger = LoggerFactory.getLogger(ClaudeAIProvider::class.java)
    private val apiUrl = "https://api.anthropic.com/v1/messages"

    override suspend fun summarizeConflict(
        resolution1: String,
        resolution2: String,
        partnershipContext: String?
    ): SummaryResult {
        val prompt = buildConflictPrompt(resolution1, resolution2, partnershipContext)

        val response = callClaude(prompt, systemPrompt = CONFLICT_SYSTEM_PROMPT)

        logger.debug("[summarizeConflict]: ${response.take(50)}")
        return parseConflictResponse(response)
    }

    override suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult {
        val prompt = buildRetroPrompt(notes)

        val response = callClaude(prompt, systemPrompt = RETRO_SYSTEM_PROMPT)

        logger.debug("[generateRetroPoints]: ${response.take(50)}")
        return parseRetroResponse(response, notes)
    }

    override suspend fun updatePartnershipContext(
        existingContext: String?,
        newConflictSummary: String?,
        newResolutions: Pair<String, String>?,
        retroSummary: String?,
        retroNotes: List<String>?
    ): String {
        val prompt = buildContextUpdatePrompt(
            existingContext,
            newConflictSummary,
            newResolutions,
            retroSummary,
            retroNotes
        )
        val response = callClaude(prompt, systemPrompt = CONTEXT_UPDATE_SYSTEM_PROMPT)

        logger.debug("[updatePartnershipContext]: ${response.take(50)}")
        return response
    }

    private suspend fun callClaude(userMessage: String, systemPrompt: String): String {
        try {
            val request = ClaudeRequest(
                model = model,
                maxTokens = 2048,
                system = systemPrompt,
                messages = listOf(ClaudeMessage(role = "user", content = userMessage))
            )

            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Claude API error: ${response.status} - $errorBody")
                throw IllegalStateException("Claude API error: ${response.status}")
            }

            val claudeResponse: ClaudeResponse = response.body()
            return claudeResponse.content.firstOrNull()?.text
                ?: throw IllegalStateException("Empty response from Claude")

        } catch (e: Exception) {
            logger.error("Failed to call Claude API", e)
            throw IllegalStateException("AI service temporarily unavailable", e)
        }
    }

    private fun buildConflictPrompt(
        resolution1: String,
        resolution2: String,
        partnershipContext: String?
    ): String {
        return buildString {
            if (partnershipContext != null) {
                appendLine("# Partnership History")
                appendLine(partnershipContext)
                appendLine()
            }

            appendLine("# Current Conflict Resolutions")
            appendLine()
            appendLine("## Partner 1's Resolution:")
            appendLine(resolution1)
            appendLine()
            appendLine("## Partner 2's Resolution:")
            appendLine(resolution2)
            appendLine()
            appendLine("Please analyze these resolutions and provide structured guidance following the JSON format specified in the system prompt.")
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
            appendLine("Please generate discussion points following the JSON format specified in the system prompt.")
        }
    }

    private fun buildContextUpdatePrompt(
        existingContext: String?,
        newConflictSummary: String?,
        newResolutions: Pair<String, String>?,
        retroSummary: String?,
        retroNotes: List<String>?
    ): String {
        return buildString {
            appendLine("# Partnership Context Update Request")
            appendLine()

            if (existingContext != null) {
                appendLine("## Existing Context:")
                appendLine(existingContext)
                appendLine()
            } else {
                appendLine("## Existing Context:")
                appendLine("(This is the first context entry for this partnership)")
                appendLine()
            }

            if (newConflictSummary != null && newResolutions != null) {
                appendLine("## New Conflict Information:")
                appendLine("Status: $newConflictSummary")
                appendLine()
                appendLine("Partner 1 Resolution:")
                appendLine(newResolutions.first)
                appendLine()
                appendLine("Partner 2 Resolution:")
                appendLine(newResolutions.second)
                appendLine()
            }

            if (retroSummary != null) {
                appendLine("## Retrospective Completed:")
                appendLine("Summary: $retroSummary")
                appendLine()
                if (retroNotes != null && retroNotes.isNotEmpty()) {
                    appendLine("Notes Discussed:")
                    retroNotes.forEach { note ->
                        appendLine("- $note")
                    }
                    appendLine()
                }
            }

            appendLine("Please create a compacted context summary (max 2000 chars) that captures key patterns, themes, and relationship dynamics.")
        }
    }

    private fun parseConflictResponse(response: String): SummaryResult {
        return try {
            // Try to parse as JSON first
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = response.substring(jsonStart, jsonEnd)
                // Parse JSON manually (simple parsing for the expected structure)
                val summary = extractJsonField(jsonStr, "summary")
                val patterns = extractJsonField(jsonStr, "patterns")
                val advice = extractJsonField(jsonStr, "advice")
                val recurringIssues = extractJsonArray(jsonStr, "recurring_issues")
                val themeTags = extractJsonArray(jsonStr, "theme_tags")

                SummaryResult(
                    summary = summary,
                    patterns = patterns,
                    advice = advice,
                    recurringIssues = recurringIssues,
                    themeTags = themeTags,
                    provider = "claude"
                )
            } else {
                // Fallback: treat entire response as summary
                SummaryResult(
                    summary = response,
                    patterns = null,
                    advice = null,
                    recurringIssues = emptyList(),
                    themeTags = listOf("general"),
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
                themeTags = listOf("general"),
                provider = "claude"
            )
        }
    }

    private fun parseRetroResponse(response: String, notes: List<NoteDTO>): RetroPointsResult {
        return try {
            val jsonStart = response.indexOf("[")
            val jsonEnd = response.lastIndexOf("]") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val pointsJson = response.substring(jsonStart, jsonEnd)
                val points = parseDiscussionPoints(pointsJson, notes)

                RetroPointsResult(
                    discussionPoints = points,
                    provider = "claude"
                )
            } else {
                // Fallback: create single discussion point
                RetroPointsResult(
                    discussionPoints = listOf(
                        DiscussionPoint(
                            theme = "Review all notes",
                            relatedNoteIds = notes.map { it.id },
                            suggestedApproach = response
                        )
                    ),
                    provider = "claude"
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to parse Claude retro response", e)
            RetroPointsResult(
                discussionPoints = listOf(
                    DiscussionPoint(
                        theme = "Discussion points",
                        relatedNoteIds = notes.map { it.id },
                        suggestedApproach = response
                    )
                ),
                provider = "claude"
            )
        }
    }

    // Simple JSON field extraction (avoiding full JSON library dependency)
    private fun extractJsonField(json: String, fieldName: String): String {
        val pattern = """"$fieldName"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }

    private fun extractJsonArray(json: String, fieldName: String): List<String> {
        val pattern = """"$fieldName"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(json) ?: return emptyList()
        val arrayContent = match.groupValues[1]

        return arrayContent
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }

    private fun parseDiscussionPoints(json: String, notes: List<NoteDTO>): List<DiscussionPoint> {
        // Simple array parsing for discussion points
        val points = mutableListOf<DiscussionPoint>()
        val itemPattern = """\{(.*?)\}""".toRegex(RegexOption.DOT_MATCHES_ALL)

        itemPattern.findAll(json).forEach { match ->
            val item = match.groupValues[1]
            val theme = extractJsonField("{$item}", "theme")
            val approach = extractJsonField("{$item}", "suggested_approach")

            points.add(
                DiscussionPoint(
                    theme = theme.ifEmpty { "Discussion topic" },
                    relatedNoteIds = notes.map { it.id }, // Associate all notes by default
                    suggestedApproach = approach.ifEmpty { "Discuss openly" }
                )
            )
        }

        return points.ifEmpty {
            listOf(
                DiscussionPoint(
                    theme = "Review notes",
                    relatedNoteIds = notes.map { it.id },
                    suggestedApproach = "Discuss each note together"
                )
            )
        }
    }

    companion object {
        private const val CONFLICT_SYSTEM_PROMPT = """You are a relationship counselor AI assistant helping couples resolve conflicts constructively.

Your role is to:
1. Analyze both partners' perspectives on a conflict resolution
2. Create a neutral summary of their agreement
3. Identify patterns from their relationship history
4. Provide actionable advice for moving forward
5. Detect recurring themes and categorize the conflict

Always respond in this exact JSON format:
{
  "summary": "We decided that... [neutral summary of the agreement, 2-3 sentences]",
  "patterns": "Based on your history... [analysis of patterns, or note if this is first conflict]",
  "advice": "1. [Specific actionable advice]\n2. [Second piece of advice]\n3. [Third piece of advice]",
  "recurring_issues": ["theme1", "theme2"],
  "theme_tags": ["communication", "expectations", "boundaries", etc.]
}

Be empathetic, constructive, and focus on solutions. Avoid judgment. Highlight alignment between partners."""

        private const val RETRO_SYSTEM_PROMPT = """You are a relationship counselor AI assistant helping couples have productive retrospectives.

Your role is to:
1. Group related notes by theme
2. Suggest discussion approaches for each theme
3. Prioritize important topics

Always respond with a JSON array of discussion points:
[
  {
    "theme": "Theme name (e.g., 'Communication expectations')",
    "suggested_approach": "Specific guidance for discussing this theme"
  },
  ...
]

Be constructive, empathetic, and focus on creating safe dialogue."""

        private const val CONTEXT_UPDATE_SYSTEM_PROMPT = """You are an AI assistant that maintains compacted relationship context summaries.

Your role is to:
1. Review existing context (if any)
2. Integrate new conflict or retrospective information
3. Identify and track recurring patterns
4. Create a concise summary (max 2000 characters)

Focus on:
- Recurring themes and patterns
- Communication styles
- Areas of growth
- Common conflict topics

Keep the summary factual, chronological, and useful for future conflict resolution."""
    }
}

// Data classes for Claude API
@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContent>
)

@Serializable
private data class ClaudeContent(
    val text: String
)
