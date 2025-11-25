package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    val id: String,
    val email: String,
    val name: String,
    val age: Int?,
    val gender: String?,
    val description: String?,
    val createdAt: String
)

@Serializable
data class UpdateUserProfileRequest(
    val name: String?,
    val age: Int?,
    val gender: String?,
    val description: String?,
)