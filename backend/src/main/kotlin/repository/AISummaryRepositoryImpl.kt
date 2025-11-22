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

    override suspend fun findByConflictForUser(
        conflictId: UUID,
        currentUserId: UUID,
        partnerUserId: UUID
    ): AISummaryDTO? = dbQuery {
        AISummaries.selectAll()
            .where { AISummaries.conflictId eq conflictId }
            .singleOrNull()
            ?.toAISummaryDTOForUser(currentUserId, partnerUserId)
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

        val currentApprover1 = summary[AISummaries.approvedByUserId1]
        val currentApprover2 = summary[AISummaries.approvedByUserId2]

        // Check if user already approved
        if (currentApprover1 == userId || currentApprover2 == userId) {
            return@dbQuery false // Already approved
        }

        // Add userId to first available slot
        AISummaries.update({ AISummaries.id eq summaryId }) {
            when {
                currentApprover1 == null -> it[approvedByUserId1] = userId
                currentApprover2 == null -> it[approvedByUserId2] = userId
                else -> return@update // Both slots full (shouldn't happen in 2-person conflict)
            }
        } > 0
    }

    override suspend fun isApprovedByBoth(summaryId: UUID): Boolean = dbQuery {
        AISummaries.selectAll()
            .where { AISummaries.id eq summaryId }
            .singleOrNull()
            ?.let { row ->
                row[AISummaries.approvedByUserId1] != null &&
                row[AISummaries.approvedByUserId2] != null
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

private fun ResultRow.toAISummaryDTOForUser(currentUserId: UUID, partnerUserId: UUID) = AISummaryDTO(
    id = this[AISummaries.id].value.toString(),
    conflictId = this[AISummaries.conflictId].value.toString(),
    summaryText = this[AISummaries.summaryText],
    provider = this[AISummaries.provider],
    approvedByMe = run {
        val approver1 = this[AISummaries.approvedByUserId1]
        val approver2 = this[AISummaries.approvedByUserId2]
        approver1 == currentUserId || approver2 == currentUserId
    },
    approvedByPartner = run {
        val approver1 = this[AISummaries.approvedByUserId1]
        val approver2 = this[AISummaries.approvedByUserId2]
        approver1 == partnerUserId || approver2 == partnerUserId
    },
    createdAt = this[AISummaries.createdAt].toString()
)
