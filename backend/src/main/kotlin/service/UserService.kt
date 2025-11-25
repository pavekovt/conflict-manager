package me.pavekovt.service

import me.pavekovt.dto.UserDTO
import me.pavekovt.repository.UserRepository
import java.util.UUID

/**
 * Simple service for user-related operations.
 * Handles basic user data retrieval and profile updates.
 */
class UserService(
    private val userRepository: UserRepository
) {
    suspend fun findByEmail(email: String): UserDTO? {
        return userRepository.findByEmail(email)
    }

    suspend fun findById(userId: UUID): UserDTO? {
        return userRepository.findById(userId)
    }

    suspend fun updateProfile(
        userId: UUID,
        name: String?,
        age: Int?,
        gender: String?,
        description: String?,
    ): UserDTO {
        return userRepository.updateProfile(userId, name, age, gender, description)
            ?: throw IllegalStateException("User not found")
    }
}
