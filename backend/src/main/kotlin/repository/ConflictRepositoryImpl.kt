package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class ConflictRepositoryImpl : ConflictRepository {

    override suspend fun create(initiatedBy: UUID): ConflictDTO = dbQuery {
        val id = Conflicts.insertReturning {
            it[Conflicts.initiatedBy] = initiatedBy
        }
            .single()[Conflicts.id].value

        buildConflictDTO(id, initiatedBy)
    }

    override suspend fun findById(conflictId: UUID): ConflictDTO? = dbQuery {
        Conflicts.selectAll()
            .where { Conflicts.id eq conflictId }
            .singleOrNull()
            ?.let { row ->
                buildConflictDTO(
                    conflictId = row[Conflicts.id].value,
                    initiatedBy = row[Conflicts.initiatedBy].value
                )
            }
    }

    override suspend fun findByUser(userId: UUID): List<ConflictDTO> = dbQuery {
        // Get conflicts where user initiated or has a resolution
        val conflictIds = Resolutions
            .selectAll()
            .where { Resolutions.userId eq userId }
            .map { it[Resolutions.conflictId].value }
            .toSet()

        val initiatedIds = Conflicts
            .selectAll()
            .where { Conflicts.initiatedBy eq userId }
            .map { it[Conflicts.id].value }
            .toSet()

        val allIds = conflictIds + initiatedIds

        allIds.mapNotNull { conflictId ->
            Conflicts.selectAll()
                .where { Conflicts.id eq conflictId }
                .singleOrNull()
                ?.let { row ->
                    buildConflictDTO(
                        conflictId = row[Conflicts.id].value,
                        initiatedBy = row[Conflicts.initiatedBy].value
                    )
                }
        }
    }

    override suspend fun updateStatus(conflictId: UUID, newStatus: ConflictStatus): Boolean = dbQuery {
        Conflicts.update({ Conflicts.id eq conflictId }) {
            it[status] = newStatus
        } > 0
    }

    override suspend fun getPartnerUserId(conflictId: UUID, currentUserId: UUID): UUID? = dbQuery {
        val allUserIds = Resolutions
            .selectAll()
            .where { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.userId].value }
            .toSet()

        val conflict = Conflicts
            .selectAll()
            .where { Conflicts.id eq conflictId }
            .singleOrNull()

        val initiatorId = conflict?.get(Conflicts.initiatedBy)?.value

        (allUserIds + setOfNotNull(initiatorId))
            .firstOrNull { it != currentUserId }
    }

    private suspend fun buildConflictDTO(conflictId: UUID, initiatedBy: UUID): ConflictDTO {
        val conflict = Conflicts.selectAll()
            .where { Conflicts.id eq conflictId }
            .single()

        val resolutionCount = Resolutions
            .selectAll()
            .where { Resolutions.conflictId eq conflictId }
            .count()

        val hasSummary = AISummaries
            .selectAll()
            .where { AISummaries.conflictId eq conflictId }
            .count() > 0

        return ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = initiatedBy.toString(),
            status = conflict[Conflicts.status].name.lowercase(),
            createdAt = conflict[Conflicts.createdAt].toString(),
            myResolutionSubmitted = resolutionCount > 0,
            partnerResolutionSubmitted = resolutionCount == 2L,
            summaryAvailable = hasSummary
        )
    }
}
