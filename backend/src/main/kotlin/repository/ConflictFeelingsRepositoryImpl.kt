package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.ConflictFeelingsDTO
import me.pavekovt.entity.ConflictFeelings
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ConflictFeelingsRepositoryImpl : ConflictFeelingsRepository {

    override suspend fun create(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String,
        aiGuidance: String,
        suggestedResolution: String
    ): ConflictFeelingsDTO = dbQuery {
        val resultRow = ConflictFeelings.insertReturning {
            it[ConflictFeelings.conflictId] = conflictId
            it[ConflictFeelings.userId] = userId
            it[ConflictFeelings.feelingsText] = feelingsText
            it[ConflictFeelings.aiGuidance] = aiGuidance
            it[ConflictFeelings.suggestedResolution] = suggestedResolution
        }.single()

        resultRow.toConflictFeelingsDTO()
    }

    override suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): ConflictFeelingsDTO? = dbQuery {
        ConflictFeelings.selectAll()
            .where { (ConflictFeelings.conflictId eq conflictId) and (ConflictFeelings.userId eq userId) }
            .singleOrNull()
            ?.toConflictFeelingsDTO()
    }

    override suspend fun hasSubmittedFeelings(conflictId: UUID, userId: UUID): Boolean = dbQuery {
        ConflictFeelings.selectAll()
            .where { (ConflictFeelings.conflictId eq conflictId) and (ConflictFeelings.userId eq userId) }
            .count() > 0
    }

    override suspend fun findByConflict(conflictId: UUID): List<ConflictFeelingsDTO> = dbQuery {
        ConflictFeelings.selectAll()
            .where { ConflictFeelings.conflictId eq conflictId }
            .map { it.toConflictFeelingsDTO() }
    }

    override suspend fun countSubmittedFeelings(conflictId: UUID): Int = dbQuery {
        ConflictFeelings.selectAll()
            .where { ConflictFeelings.conflictId eq conflictId }
            .count()
            .toInt()
    }
}

private fun ResultRow.toConflictFeelingsDTO() = ConflictFeelingsDTO(
    id = this[ConflictFeelings.id].value.toString(),
    conflictId = this[ConflictFeelings.conflictId].value.toString(),
    userId = this[ConflictFeelings.userId].value.toString(),
    feelingsText = this[ConflictFeelings.feelingsText],
    aiGuidance = this[ConflictFeelings.aiGuidance],
    suggestedResolution = this[ConflictFeelings.suggestedResolution],
    submittedAt = this[ConflictFeelings.submittedAt].toString()
)
