package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object AISummaries : UUIDTable("ai_summaries") {
    val conflictId = reference("conflict_id", Conflicts)
    val summaryText = text("summary_text") // "We decided that..." statement
    val patterns = text("patterns").nullable() // Patterns noticed from historical context
    val advice = text("advice").nullable() // Actionable relationship advice
    val recurringIssues = text("recurring_issues").nullable() // JSON array of recurring themes
    val themeTags = text("theme_tags").nullable() // JSON array of AI-suggested categories
    val provider = varchar("provider", 50)
    val approvedByUserId1 = uuid("approved_by_user_id_1").nullable()
    val approvedByUserId2 = uuid("approved_by_user_id_2").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
