package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object JournalEntries : UUIDTable("journal_entries") {
    val userId = reference("user_id", Users)
    val partnershipId = reference("partnership_id", Partnerships)
    val content = text("content")
    val status = enumerationByName<JournalStatus>("status", 50).default(JournalStatus.DRAFT)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val completedAt = datetime("completed_at").nullable()
}

enum class JournalStatus {
    DRAFT,          // User is still writing
    COMPLETED,      // User finished, ready for AI processing
    AI_PROCESSED,   // AI has incorporated into context
    ARCHIVED        // User archived (hidden from active view)
}
