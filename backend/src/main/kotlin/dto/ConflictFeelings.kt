package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConflictFeelingsDTO(
    val id: String,
    val conflictId: String,
    val userId: String,
    val feelingsText: String,
    val aiGuidance: String?,
    val suggestedResolution: String?,
    val submittedAt: String
)

@Serializable
data class FeelingsProcessingResult(
    val guidance: String,           // Empathetic AI response to help process emotions
    val suggestedResolution: String, // AI's suggested resolution approach
    val emotionalTone: String       // Detected tone: "frustrated", "hurt", "angry", "sad", etc.
)
