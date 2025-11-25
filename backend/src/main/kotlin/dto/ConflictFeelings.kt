package me.pavekovt.dto

import kotlinx.serialization.Serializable
import me.pavekovt.entity.ConflictFeelingsStatus

@Serializable
data class ConflictFeelingsDTO(
    val id: String,
    val conflictId: String,
    val userId: String,
    val feelingsText: String,
    val status: ConflictFeelingsStatus,
    val aiGuidance: String?,
    val suggestedResolution: String?,
    val emotionalTone: String?,
    val submittedAt: String,
    // Privacy UX
    val visibleToPartner: Boolean = false,
    val visibilityReason: String? = null
)

@Serializable
data class FeelingsProcessingResult(
    val guidance: String,           // Empathetic AI response to help process emotions
    val suggestedResolution: String, // AI's suggested resolution approach
    val emotionalTone: String       // Detected tone: "frustrated", "hurt", "angry", "sad", etc.
)
