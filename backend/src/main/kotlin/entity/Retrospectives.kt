package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Retrospectives : UUIDTable("retrospectives") {
    val scheduledDate = datetime("scheduled_date").nullable()
    val startedAt = datetime("started_at").defaultExpression(CurrentDateTime)
    val completedAt = datetime("completed_at").nullable()
    val status = enumerationByName<RetroStatus>("status", 50)
    val aiDiscussionPoints = text("ai_discussion_points").nullable() // JSON array of discussion points
    val finalSummary = text("final_summary").nullable()
    val approvedByUserId1 = uuid("approved_by_user_id_1").nullable()
    val approvedByUserId2 = uuid("approved_by_user_id_2").nullable()
    val approvalText1 = text("approval_text_1").nullable() // User 1's explanation for approval
    val approvalText2 = text("approval_text_2").nullable() // User 2's explanation for approval
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

object RetrospectiveNotes : Table("retrospective_notes") {
    val retrospectiveId = reference("retrospective_id", Retrospectives)
    val noteId = reference("note_id", Notes)

    override val primaryKey = PrimaryKey(retrospectiveId, noteId)
}

object RetrospectiveUsers : Table("retrospective_users") {
    val retrospectiveId = reference("retrospective_id", Retrospectives)
    val userId = reference("user_id", Users)

    override val primaryKey = PrimaryKey(retrospectiveId, userId)
}

enum class RetroStatus {
    SCHEDULED,
    IN_PROGRESS,
    PROCESSING_DISCUSSION_POINTS,  // AI is generating discussion points in background
    PENDING_APPROVAL,              // Discussion points generated, waiting for both users to approve
    COMPLETED,
    CANCELLED
}
