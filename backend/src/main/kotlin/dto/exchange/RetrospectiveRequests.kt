package me.pavekovt.dto.exchange

data class CreateRetrospectiveRequest(
    val scheduledDate: String?
)

data class AddNoteToRetroRequest(
    val noteId: String
)

data class CompleteRetrospectiveRequest(
    val finalSummary: String
)
