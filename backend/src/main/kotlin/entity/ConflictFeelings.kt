package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Stores each partner's feelings and frustrations about a conflict.
 * Users can submit multiple feelings as they process the conflict.
 * AI processes these feelings and provides guidance + suggested resolution.
 */
object ConflictFeelings : UUIDTable("conflict_feelings") {
    val conflictId = reference("conflict_id", Conflicts)
    val userId = reference("user_id", Users)
    val feelingsText = text("feelings_text") // User's frustrations, emotions, perspective
    val detectedLanguage = varchar("detected_language", 10).nullable() // Language detected from user input
    val status = enumerationByName<ConflictFeelingsStatus>("status", 20).default(ConflictFeelingsStatus.PROCESSING)
    val aiGuidance = text("ai_guidance").nullable() // AI's empathetic response and processing help
    val suggestedResolution = text("suggested_resolution").nullable() // AI's suggested resolution approach
    val emotionalTone = varchar("emotional_tone", 50).nullable() // Detected emotional tone
    val submittedAt = datetime("submitted_at").defaultExpression(CurrentDateTime)
}

enum class ConflictFeelingsStatus {
    PROCESSING,    // AI is processing this feeling in background
    COMPLETED,     // AI has processed and guidance is available
    FAILED         // AI processing failed
}
