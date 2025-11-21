package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.DecisionStatus
import me.pavekovt.entity.Decisions
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class DecisionRepositoryImpl : DecisionRepository {

    override suspend fun create(conflictId: UUID?, summary: String, category: String?): DecisionDTO = dbQuery {
        Decisions.insertReturning {
            it[Decisions.conflictId] = conflictId
            it[Decisions.summary] = summary
            it[Decisions.category] = category
        }
            .single()
            .toDecisionDTO()
    }

    override suspend fun findAll(status: DecisionStatus?): List<DecisionDTO> = dbQuery {
        Decisions.selectAll()
            .apply {
                if (status != null) {
                    andWhere { Decisions.status eq status }
                }
            }
            .orderBy(Decisions.createdAt to SortOrder.DESC)
            .map { it.toDecisionDTO() }
    }

    override suspend fun findById(id: UUID): DecisionDTO? = dbQuery {
        Decisions.selectAll()
            .where { Decisions.id eq id }
            .singleOrNull()
            ?.toDecisionDTO()
    }

    override suspend fun markReviewed(id: UUID): Boolean = dbQuery {
        Decisions.update({ Decisions.id eq id }) {
            it[status] = DecisionStatus.REVIEWED
            it[reviewedAt] = CurrentDateTime
        } > 0
    }

    override suspend fun archive(id: UUID): Boolean = dbQuery {
        Decisions.update({ Decisions.id eq id }) {
            it[status] = DecisionStatus.ARCHIVED
        } > 0
    }
}

private fun ResultRow.toDecisionDTO() = DecisionDTO(
    id = this[Decisions.id].value.toString(),
    conflictId = this[Decisions.conflictId]?.value?.toString(),
    summary = this[Decisions.summary],
    category = this[Decisions.category],
    status = this[Decisions.status].name.lowercase(),
    createdAt = this[Decisions.createdAt].toString(),
    reviewedAt = this[Decisions.reviewedAt]?.toString()
)
