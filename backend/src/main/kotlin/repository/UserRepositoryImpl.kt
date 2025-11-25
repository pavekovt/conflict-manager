package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.UserDTO
import me.pavekovt.entity.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

class UserRepositoryImpl : UserRepository {
    override suspend fun create(email: String, passwordHash: String, name: String): UserDTO = dbQuery {
        Users.insertReturning {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.name] = name
        }
            .single()
            .toUserDTO()
    }

    override suspend fun findByEmail(email: String): UserDTO? = dbQuery {
        Users.selectAll()
            .where { Users.email eq email }
            .singleOrNull()
            ?.toUserDTO()
    }

    override suspend fun getUserPassword(email: String): String? = dbQuery {
        Users.select(Users.passwordHash)
            .where { Users.email eq email }
            .singleOrNull()
            ?.get(Users.passwordHash)
    }

    override suspend fun findById(id: UUID): UserDTO? = dbQuery {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.toUserDTO()
    }

    override suspend fun updateNotificationToken(userId: UUID, token: String?): Unit = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[notificationToken] = token
        }
    }

    override suspend fun updateProfile(
        userId: UUID,
        name: String?,
        age: Int?,
        gender: String?,
        description: String?,
    ): UserDTO? = dbQuery {
        Users.update({ Users.id eq userId }) {
            if (name != null) it[Users.name] = name
            if (age != null) it[Users.age] = age
            if (gender != null) it[Users.gender] = gender
            if (description != null) it[Users.description] = description
        }

        findById(userId)
    }
}

// Extension function to map ResultRow to DTO
private fun ResultRow.toUserDTO() = UserDTO(
    id = this[Users.id].value.toString(),
    email = this[Users.email],
    name = this[Users.name],
    age = this[Users.age],
    gender = this[Users.gender],
    description = this[Users.description],
    createdAt = this[Users.createdAt].toString()
)