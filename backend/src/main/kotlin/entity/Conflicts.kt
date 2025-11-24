package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Conflicts : UUIDTable("conflicts") {
    val initiatedBy = reference("initiated_by", Users)
    val status = enumerationByName<ConflictStatus>("status", 50).default(ConflictStatus.PENDING_FEELINGS)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

enum class ConflictStatus {
    PENDING_FEELINGS,         // Waiting for both partners to submit their feelings/frustrations
    PROCESSING_FEELINGS,      // AI is processing submitted feelings in background
    PENDING_RESOLUTIONS,      // Both submitted feelings, AI provided guidance, waiting for final resolutions
    PROCESSING_SUMMARY,       // AI is generating summary from both resolutions in background
    SUMMARY_GENERATED,        // AI created final summary from both resolutions
    REFINEMENT,               // One or both partners requested changes to summary
    APPROVED,                 // Both partners approved summary, decision created
    ARCHIVED                  // Conflict closed without completion
}
