package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class RetrospectiveDTO(
    val id: String,
    val scheduledDate: String?,
    val startedAt: String,
    val completedAt: String?,
    val status: String,
    val aiDiscussionPoints: String?,
    val finalSummary: String?,
    val createdAt: String
)

@Serializable
data class RetrospectiveWithNotesDTO(
    val id: String,
    val scheduledDate: String?,
    val startedAt: String,
    val completedAt: String?,
    val status: String,
    val aiDiscussionPoints: String?,
    val finalSummary: String?,
    val notes: List<NoteDTO>,
    val createdAt: String
)
