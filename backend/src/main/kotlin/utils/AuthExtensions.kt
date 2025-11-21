package me.pavekovt.utils

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import me.pavekovt.dto.UserDTO
import me.pavekovt.repository.UserRepository
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Get the current authenticated user from JWT token.
 * Throws IllegalStateException if user is not authenticated or not found.
 */
suspend fun ApplicationCall.getCurrentUser(): UserDTO {
    val userRepository by inject<UserRepository>()

    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("User not authenticated")

    val email = principal["username"]
        ?: throw IllegalStateException("No username in token")

    return userRepository.findByEmail(email)
        ?: throw IllegalStateException("User not found")
}

/**
 * Get the current authenticated user's ID as UUID.
 */
suspend fun ApplicationCall.getCurrentUserId(): UUID {
    return UUID.fromString(getCurrentUser().id)
}
