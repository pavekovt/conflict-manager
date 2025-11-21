package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object AISummaries : UUIDTable("ai_summaries") {
    val conflictId = reference("conflict_id", Conflicts)
    val summaryText = text("summary_text")
    val provider = varchar("provider", 50)
    val approvedByUser1 = bool("approved_by_user_1").default(false)
    val approvedByUser2 = bool("approved_by_user_2").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
