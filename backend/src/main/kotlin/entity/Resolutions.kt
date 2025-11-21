package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Resolutions : UUIDTable("resolutions") {
    val conflictId = reference("conflict_id", Conflicts)
    val userId = reference("user_id", Users)
    val resolutionText = text("resolution_text")
    val submittedAt = datetime("submitted_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex(conflictId, userId)
    }
}
