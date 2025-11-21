package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class NoteDTO(
    val id: String,
    val userId: String,
    val content: String,
    val status: String,
    val mood: String?,
    val createdAt: String
)
