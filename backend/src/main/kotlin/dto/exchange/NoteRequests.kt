package me.pavekovt.dto.exchange

import kotlinx.serialization.Serializable

@Serializable
data class CreateNoteRequest(
    val content: String,
    val mood: String? = null
)

@Serializable
data class UpdateNoteRequest(
    val content: String? = null,
    val status: String? = null,
    val mood: String? = null
)
