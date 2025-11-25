package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class JournalEntryDTO(
    val id: String,
    val userId: String,
    val partnershipId: String,
    val content: String,
    val status: String, // draft, completed, ai_processed, archived
    val createdAt: String,
    val completedAt: String?,
    // Privacy UX
    val privacy: PrivacyLevel = PrivacyLevel.PRIVATE,
    val aiInsights: JournalAIInsights? = null
)

@Serializable
enum class PrivacyLevel {
    PRIVATE,           // Only me
    AI_ONLY,          // AI can see for context, partner cannot
    PARTNER_VISIBLE   // Partner can read
}

@Serializable
data class JournalAIInsights(
    val extractedThemes: List<String> = emptyList(),
    val emotionalTone: String? = null,
    val addedToContext: Boolean = false,
    val processedAt: String? = null
)
