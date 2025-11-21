package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Notes : UUIDTable("notes") {
    val userId = reference("user_id", Users)
    val content = text("content")
    val status = enumerationByName<NoteStatus>("status", 50).default(NoteStatus.DRAFT)
    val mood = enumerationByName<Mood>("mood", 50).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

enum class NoteStatus {
    DRAFT,
    READY_FOR_DISCUSSION,
    DISCUSSED,
    ARCHIVED
}

enum class Mood {
    FRUSTRATED,
    ANGRY,
    SAD,
    CONCERNED,
    NEUTRAL
}
