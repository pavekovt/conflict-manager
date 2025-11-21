package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Decisions : UUIDTable("decisions") {
    val conflictId = reference("conflict_id", Conflicts).nullable()
    val summary = text("summary")
    val category = varchar("category", 100).nullable()
    val status = enumerationByName<DecisionStatus>("status", 50).default(DecisionStatus.ACTIVE)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val reviewedAt = datetime("reviewed_at").nullable()
}

enum class DecisionStatus {
    ACTIVE,
    REVIEWED,
    ARCHIVED
}
