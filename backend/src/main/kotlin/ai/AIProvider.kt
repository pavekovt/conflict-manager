package me.pavekovt.ai

import kotlinx.serialization.Serializable
import me.pavekovt.dto.FeelingsProcessingResult
import me.pavekovt.dto.NoteDTO

interface AIProvider {
    /**
     * Process a user's feelings and frustrations about a conflict.
     * Provides empathetic guidance and suggests a resolution approach.
     * This is the FIRST step in the conflict resolution process.
     *
     * @param userFeelings The user's current feelings text
     * @param userProfile Profile of the user submitting feelings (name, age, gender, description)
     * @param partnerProfile Profile of their partner for context
     * @param partnershipContext Historical context of the relationship
     * @param previousFeelings Previous feelings from this user in this conflict
     * @param detectedLanguage Language to respond in (auto-detected from input)
     */
    suspend fun processFeelingsAndSuggestResolution(
        userFeelings: String,
        userProfile: UserProfile,
        partnerProfile: UserProfile,
        partnershipContext: String? = null,
        previousFeelings: List<String>? = null,
        detectedLanguage: String = "en"
    ): FeelingsProcessingResult

    /**
     * Generate a comprehensive conflict summary with relationship advice.
     * Uses historical partnership context to provide relevant patterns and advice.
     *
     * @param resolution1 First partner's resolution text
     * @param resolution2 Second partner's resolution text
     * @param user1Profile First partner's profile
     * @param user2Profile Second partner's profile
     * @param partnershipContext Historical context of the relationship
     * @param detectedLanguage Language to respond in
     */
    suspend fun summarizeConflict(
        resolution1: String,
        resolution2: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile,
        partnershipContext: String? = null,
        detectedLanguage: String = "en"
    ): SummaryResult

    /**
     * Generate discussion points for retrospective based on notes.
     */
    suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult

    /**
     * Update the compacted partnership context with new conflict resolution.
     * Called asynchronously after conflict is approved by both partners.
     *
     * @param existingContext Current partnership context
     * @param conflictSummary The approved conflict summary
     * @param user1Profile First partner's profile
     * @param user2Profile Second partner's profile
     */
    suspend fun updatePartnershipContextWithConflict(
        existingContext: String?,
        conflictSummary: String,
        user1Profile: UserProfile,
        user2Profile: UserProfile
    ): String

    /**
     * Update the compacted partnership context with retrospective insights.
     * Called synchronously after retrospective completion.
     *
     * @param existingContext Current partnership context
     * @param retroSummary The retrospective final summary
     * @param retroNotes Notes discussed in the retrospective
     */
    suspend fun updatePartnershipContextWithRetrospective(
        existingContext: String?,
        retroSummary: String,
        retroNotes: List<String>
    ): String

    /**
     * Detect language from user input text
     */
    fun detectLanguage(text: String): String
}

/**
 * User profile context for AI interactions
 */
data class UserProfile(
    val name: String,
    val age: Int?,
    val gender: String?,
    val description: String?
) {
    fun toContextString(): String {
        val parts = mutableListOf<String>()
        parts.add("Name: $name")
        if (age != null) parts.add("Age: $age")
        if (gender != null) parts.add("Gender: $gender")
        if (description != null) parts.add("About: $description")
        return parts.joinToString(", ")
    }
}

/**
 * Enhanced result from AI conflict summarization.
 * Includes multi-part structure for UI flexibility.
 */
@Serializable
data class SummaryResult(
    val summary: String, // "We decided that..." statement
    val patterns: String?, // Patterns noticed from historical context
    val advice: String?, // Actionable relationship advice
    val recurringIssues: List<String>, // Specific recurring themes
    val themeTags: List<String>, // AI-suggested categories (e.g., "communication", "finances")
    val provider: String
)

@Serializable
data class RetroPointsResult(
    val discussionPoints: List<DiscussionPoint>,
    val provider: String
)

@Serializable
data class DiscussionPoint(
    val theme: String,
    val relatedNoteIds: List<String>,
    val suggestedApproach: String
)
