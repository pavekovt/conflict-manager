package me.pavekovt.dto.exchange

data class CreateRetrospectiveRequest(
    val scheduledDate: String?,
    val userIds: List<String>? = null  // Optional: if not provided, only current user is added
)

data class AddNoteToRetroRequest(
    val noteId: String
)

data class CompleteRetrospectiveRequest(
    val finalSummary: String
)
