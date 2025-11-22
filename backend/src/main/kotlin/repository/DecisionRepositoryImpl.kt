package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.Conflicts
import me.pavekovt.entity.DecisionStatus
import me.pavekovt.entity.Decisions
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
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

    override suspend fun findByUserAndPartner(
        userId: UUID,
        partnerId: UUID,
        status: DecisionStatus?
    ): List<DecisionDTO> = dbQuery {
        // Get all decisions that are linked to conflicts
        val decisionsQuery = Decisions.selectAll()
            .apply {
                if (status != null) {
                    andWhere { Decisions.status eq status }
                }
            }
            .orderBy(Decisions.createdAt to SortOrder.DESC)

        // Filter by checking each decision's conflict
        decisionsQuery
            .filter { row ->
                val conflictId = row[Decisions.conflictId]
                if (conflictId == null) {
                    false
                } else {
                    // Check if conflict was initiated by user or partner
                    val conflict = Conflicts.selectAll()
                        .where { Conflicts.id eq conflictId }
                        .singleOrNull()
                    conflict != null && (conflict[Conflicts.initiatedBy].value == userId || conflict[Conflicts.initiatedBy].value == partnerId)
                }
            }
            .map { it.toDecisionDTO() }
    }

    override suspend fun isAccessibleByUser(
        decisionId: UUID,
        userId: UUID,
        partnerId: UUID
    ): Boolean = dbQuery {
        val decision = Decisions.selectAll()
            .where { Decisions.id eq decisionId }
            .singleOrNull() ?: return@dbQuery false

        val conflictId = decision[Decisions.conflictId] ?: return@dbQuery false

        // Check if conflict was initiated by user or their partner
        val conflict = Conflicts.selectAll()
            .where { Conflicts.id eq conflictId }
            .singleOrNull() ?: return@dbQuery false

        val initiatorId = conflict[Conflicts.initiatedBy].value
        initiatorId == userId || initiatorId == partnerId
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
