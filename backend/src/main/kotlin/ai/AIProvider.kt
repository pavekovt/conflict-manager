package me.pavekovt.ai

import me.pavekovt.dto.FeelingsProcessingResult
import me.pavekovt.dto.NoteDTO

interface AIProvider {
    /**
     * Process a user's feelings and frustrations about a conflict.
     * Provides empathetic guidance and suggests a resolution approach.
     * This is the FIRST step in the conflict resolution process.
     */
    suspend fun processFeelingsAndSuggestResolution(
        userFeelings: String,
        partnershipContext: String? = null
    ): FeelingsProcessingResult

    /**
     * Generate a comprehensive conflict summary with relationship advice.
     * Uses historical partnership context to provide relevant patterns and advice.
     */
    suspend fun summarizeConflict(
        resolution1: String,
        resolution2: String,
        partnershipContext: String? = null
    ): SummaryResult

    /**
     * Generate discussion points for retrospective based on notes.
     */
    suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult

    /**
     * Update the compacted partnership context summary with new information.
     * Called after each conflict resolution and retrospective completion.
     */
    suspend fun updatePartnershipContext(
        existingContext: String?,
        newConflictSummary: String? = null,
        newResolutions: Pair<String, String>? = null,
        retroSummary: String? = null,
        retroNotes: List<String>? = null
    ): String
}

/**
 * Enhanced result from AI conflict summarization.
 * Includes multi-part structure for UI flexibility.
 */
data class SummaryResult(
    val summary: String, // "We decided that..." statement
    val patterns: String?, // Patterns noticed from historical context
    val advice: String?, // Actionable relationship advice
    val recurringIssues: List<String>, // Specific recurring themes
    val themeTags: List<String>, // AI-suggested categories (e.g., "communication", "finances")
    val provider: String
)

data class RetroPointsResult(
    val discussionPoints: List<DiscussionPoint>,
    val provider: String
)

data class DiscussionPoint(
    val theme: String,
    val relatedNoteIds: List<String>,
    val suggestedApproach: String
)
