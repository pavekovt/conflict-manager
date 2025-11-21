package me.pavekovt.dto.exchange

data class LoginRequest(
    val email: String,
    val password: String,
)