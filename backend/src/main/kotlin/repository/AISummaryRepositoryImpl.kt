package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.entity.AISummaries
import me.pavekovt.entity.Resolutions
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class AISummaryRepositoryImpl : AISummaryRepository {

    override suspend fun create(conflictId: UUID, summaryText: String, provider: String): UUID = dbQuery {
        AISummaries.insertReturning {
            it[AISummaries.conflictId] = conflictId
            it[AISummaries.summaryText] = summaryText
            it[AISummaries.provider] = provider
        }
            .single()[AISummaries.id].value
    }

    override suspend fun findByConflict(conflictId: UUID): AISummaryDTO? = dbQuery {
        AISummaries.selectAll()
            .where { AISummaries.conflictId eq conflictId }
            .singleOrNull()
            ?.toAISummaryDTO()
    }

    override suspend fun findById(summaryId: UUID): AISummaryDTO? = dbQuery {
        AISummaries.selectAll()
            .where { AISummaries.id eq summaryId }
            .singleOrNull()
            ?.toAISummaryDTO()
    }

    override suspend fun approve(summaryId: UUID, userId: UUID, conflictId: UUID): Boolean = dbQuery {
        val summary = AISummaries.selectAll()
            .where { AISummaries.id eq summaryId }
            .singleOrNull() ?: return@dbQuery false

        // Get both users involved in conflict (sorted for consistency)
        val userIds = Resolutions
            .selectAll()
            .where { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.userId].value }
            .sorted()

        if (userIds.size != 2) return@dbQuery false

        val isUser1 = userId == userIds[0]

        AISummaries.update({ AISummaries.id eq summaryId }) {
            if (isUser1) {
                it[approvedByUser1] = true
            } else {
                it[approvedByUser2] = true
            }
        } > 0
    }

    override suspend fun isApprovedByBoth(summaryId: UUID): Boolean = dbQuery {
        AISummaries.selectAll()
            .where { AISummaries.id eq summaryId }
            .singleOrNull()
            ?.let { row ->
                row[AISummaries.approvedByUser1] && row[AISummaries.approvedByUser2]
            } ?: false
    }
}

private fun ResultRow.toAISummaryDTO() = AISummaryDTO(
    id = this[AISummaries.id].value.toString(),
    conflictId = this[AISummaries.conflictId].value.toString(),
    summaryText = this[AISummaries.summaryText],
    provider = this[AISummaries.provider],
    approvedByMe = false, // Will be set by service layer based on current user
    approvedByPartner = false,
    createdAt = this[AISummaries.createdAt].toString()
)
