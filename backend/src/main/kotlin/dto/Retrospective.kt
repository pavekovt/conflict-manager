package me.pavekovt.dto

import kotlinx.serialization.Serializable
import me.pavekovt.ai.DiscussionPoint

@Serializable
data class RetrospectiveDTO(
    val id: String,
    val scheduledDate: String?,
    val startedAt: String,
    val completedAt: String?,
    val status: String,
    val aiDiscussionPoints: List<DiscussionPoint>?,
    val finalSummary: String?,
    val approvedByUserId1: String?,
    val approvedByUserId2: String?,
    val approvalText1: String?,
    val approvalText2: String?,
    val createdAt: String
)

@Serializable
data class RetrospectiveWithNotesDTO(
    val id: String,
    val scheduledDate: String?,
    val startedAt: String,
    val completedAt: String?,
    val status: String,
    val aiDiscussionPoints: List<DiscussionPoint>?,
    val finalSummary: String?,
    val approvedByUserId1: String?,
    val approvedByUserId2: String?,
    val approvalText1: String?,
    val approvalText2: String?,
    val notes: List<NoteDTO>,
    val createdAt: String
)
