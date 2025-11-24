package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.ConflictFeelingsDTO
import me.pavekovt.entity.ConflictFeelings
import me.pavekovt.entity.ConflictFeelingsStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class ConflictFeelingsRepositoryImpl : ConflictFeelingsRepository {

    override suspend fun create(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String
    ): ConflictFeelingsDTO = dbQuery {
        val resultRow = ConflictFeelings.insertReturning {
            it[ConflictFeelings.conflictId] = conflictId
            it[ConflictFeelings.userId] = userId
            it[ConflictFeelings.feelingsText] = feelingsText
            // status defaults to PROCESSING
            // aiGuidance, suggestedResolution, emotionalTone remain null until processed
        }.single()

        resultRow.toConflictFeelingsDTO()
    }

    override suspend fun findById(feelingId: UUID): ConflictFeelingsDTO? = dbQuery {
        ConflictFeelings.selectAll()
            .where { ConflictFeelings.id eq feelingId }
            .singleOrNull()
            ?.toConflictFeelingsDTO()
    }

    override suspend fun updateWithAIResponse(
        feelingId: UUID,
        aiGuidance: String,
        suggestedResolution: String,
        emotionalTone: String
    ): ConflictFeelingsDTO? = dbQuery {
        ConflictFeelings.update({ ConflictFeelings.id eq feelingId }) {
            it[ConflictFeelings.aiGuidance] = aiGuidance
            it[ConflictFeelings.suggestedResolution] = suggestedResolution
            it[ConflictFeelings.emotionalTone] = emotionalTone
            it[ConflictFeelings.status] = ConflictFeelingsStatus.COMPLETED
        }

        findById(feelingId)
    }

    override suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): List<ConflictFeelingsDTO> = dbQuery {
        ConflictFeelings.selectAll()
            .where { (ConflictFeelings.conflictId eq conflictId) and (ConflictFeelings.userId eq userId) }
            .orderBy(ConflictFeelings.submittedAt)
            .map { it.toConflictFeelingsDTO() }
    }

    override suspend fun findByConflict(conflictId: UUID): List<ConflictFeelingsDTO> = dbQuery {
        ConflictFeelings.selectAll()
            .where { ConflictFeelings.conflictId eq conflictId }
            .orderBy(ConflictFeelings.submittedAt)
            .map { it.toConflictFeelingsDTO() }
    }

    override suspend fun countCompletedFeelings(conflictId: UUID): Int = dbQuery {
        ConflictFeelings.selectAll()
            .where {
                (ConflictFeelings.conflictId eq conflictId) and
                (ConflictFeelings.status eq ConflictFeelingsStatus.COMPLETED)
            }
            .count()
            .toInt()
    }
}

private fun ResultRow.toConflictFeelingsDTO() = ConflictFeelingsDTO(
    id = this[ConflictFeelings.id].value.toString(),
    conflictId = this[ConflictFeelings.conflictId].value.toString(),
    userId = this[ConflictFeelings.userId].value.toString(),
    feelingsText = this[ConflictFeelings.feelingsText],
    status = this[ConflictFeelings.status],
    aiGuidance = this[ConflictFeelings.aiGuidance],
    suggestedResolution = this[ConflictFeelings.suggestedResolution],
    emotionalTone = this[ConflictFeelings.emotionalTone],
    submittedAt = this[ConflictFeelings.submittedAt].toString()
)
