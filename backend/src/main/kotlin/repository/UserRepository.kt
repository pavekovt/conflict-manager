package me.pavekovt.repository

import me.pavekovt.dto.UserDTO
import java.util.UUID

interface UserRepository {
    suspend fun create(email: String, passwordHash: String, name: String): UserDTO
    suspend fun findByEmail(email: String): UserDTO?
    suspend fun findById(id: UUID): UserDTO?
    suspend fun getUserPassword(email: String): String?
    suspend fun updateNotificationToken(userId: UUID, token: String?)
    suspend fun updateProfile(
        userId: UUID,
        name: String?,
        age: Int?,
        gender: String?,
        description: String?,
    ): UserDTO?
}