package me.pavekovt.dto.exchange

import kotlinx.serialization.Serializable
import me.pavekovt.dto.UserDTO

@Serializable
data class AuthResponse(
    val token: String,
    val expiresIn: Long = 3600000, // 1 hour in milliseconds
    val user: UserDTO? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)
