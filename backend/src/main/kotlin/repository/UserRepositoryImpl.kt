package repository

import me.pavekovt.dto.UserDTO
import me.pavekovt.entity.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class UserRepositoryImpl : UserRepository {
    override suspend fun create(email: String, passwordHash: String, name: String): UserDTO = suspendTransaction {
        Users.insertReturning {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.name] = name
        }
            .single()
            .toUserDTO()
    }

    override suspend fun findByEmail(email: String): UserDTO? = suspendTransaction {
        Users.selectAll()
            .where { Users.email eq email }
            .singleOrNull()
            ?.toUserDTO()
    }

    override suspend fun getUserPassword(email: String): String? = suspendTransaction {
        Users.select(Users.passwordHash)
            .where { Users.email eq email }
            .singleOrNull()
            ?.get(Users.passwordHash)
    }

    override suspend fun findById(id: UUID): UserDTO? = suspendTransaction {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.toUserDTO()
    }

    override suspend fun updateNotificationToken(userId: UUID, token: String?): Unit = suspendTransaction {
        Users.update({ Users.id eq userId }) {
            it[notificationToken] = token
        }
    }
}

// Extension function to map ResultRow to DTO
private fun ResultRow.toUserDTO() = UserDTO(
    id = this[Users.id].value.toString(),
    email = this[Users.email],
    name = this[Users.name],
    createdAt = this[Users.createdAt].toString()
)