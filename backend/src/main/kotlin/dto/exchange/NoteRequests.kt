package me.pavekovt.dto.exchange

data class CreateNoteRequest(
    val content: String,
    val mood: String?
)

data class UpdateNoteRequest(
    val content: String?,
    val status: String?,
    val mood: String?
)
