package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Conflicts : UUIDTable("conflicts") {
    val initiatedBy = reference("initiated_by", Users)
    val status = enumerationByName<ConflictStatus>("status", 50).default(ConflictStatus.PENDING_RESOLUTIONS)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

enum class ConflictStatus {
    PENDING_RESOLUTIONS,
    SUMMARY_GENERATED,
    REFINEMENT,
    APPROVED,
    ARCHIVED
}
