package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: String
)