package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Stores each partner's initial feelings and frustrations about a conflict.
 * AI processes these feelings and provides guidance + suggested resolution.
 */
object ConflictFeelings : UUIDTable("conflict_feelings") {
    val conflictId = reference("conflict_id", Conflicts)
    val userId = reference("user_id", Users)
    val feelingsText = text("feelings_text") // User's frustrations, emotions, perspective
    val aiGuidance = text("ai_guidance").nullable() // AI's empathetic response and processing help
    val suggestedResolution = text("suggested_resolution").nullable() // AI's suggested resolution approach
    val submittedAt = datetime("submitted_at").defaultExpression(CurrentDateTime)

    init {
        // Each user can only submit feelings once per conflict
        uniqueIndex(conflictId, userId)
    }
}
