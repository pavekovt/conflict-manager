package me.pavekovt.dto.exchange

import kotlinx.serialization.Serializable

@Serializable
data class CreateRetrospectiveRequest(
    val scheduledDate: String? = null,
    val userIds: List<String>? = null  // Optional: if not provided, only current user is added
)

@Serializable
data class AddNoteToRetroRequest(
    val noteId: String
)

@Serializable
data class CompleteRetrospectiveRequest(
    val finalSummary: String
)
