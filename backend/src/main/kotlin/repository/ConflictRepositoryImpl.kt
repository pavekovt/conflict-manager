package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class ConflictRepositoryImpl : ConflictRepository {

    override suspend fun create(initiatedBy: UUID): ConflictDTO = dbQuery {
        val id = Conflicts.insertReturning {
            it[Conflicts.initiatedBy] = initiatedBy
        }
            .single()[Conflicts.id].value

        buildConflictDTO(id, initiatedBy, initiatedBy)
    }

    override suspend fun findById(conflictId: UUID, currentUserId: UUID): ConflictDTO? = dbQuery {
        Conflicts.selectAll()
            .where { Conflicts.id eq conflictId }
            .singleOrNull()
            ?.let { row ->
                buildConflictDTO(
                    conflictId = row[Conflicts.id].value,
                    initiatedBy = row[Conflicts.initiatedBy].value,
                    currentUserId = currentUserId
                )
            }
    }

    override suspend fun findByUser(userId: UUID, partnersIds: List<UUID>): List<ConflictDTO> = dbQuery {
        // Get conflicts where user initiated or has a resolution
        val initiated = Conflicts
            .selectAll()
            .where { Conflicts.initiatedBy eq userId }
            .map { row ->
                buildConflictDTO(
                    row[Conflicts.id].value,
                    row[Conflicts.initiatedBy].value,
                    userId
                )
            }

        val partners = Conflicts
            .selectAll()
            .where { Conflicts.initiatedBy inList partnersIds }
            .map { row ->
                buildConflictDTO(
                    row[Conflicts.id].value,
                    row[Conflicts.initiatedBy].value,
                    userId
                )
            }

        return@dbQuery initiated + partners
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

    private suspend fun buildConflictDTO(conflictId: UUID, initiatedBy: UUID, currentUserId: UUID): ConflictDTO {
        val conflict = Conflicts.selectAll()
            .where { Conflicts.id eq conflictId }
            .single()

        // Check if current user has submitted resolution
        val myResolution = Resolutions
            .selectAll()
            .where { (Resolutions.conflictId eq conflictId) and (Resolutions.userId eq currentUserId) }
            .singleOrNull()

        // Check if partner has submitted resolution
        val allResolutions = Resolutions
            .selectAll()
            .where { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.userId].value }

        val partnerHasResolution = allResolutions.any { it != currentUserId }

        val hasSummary = AISummaries
            .selectAll()
            .where { AISummaries.conflictId eq conflictId }
            .count() > 0

        return ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = initiatedBy.toString(),
            status = conflict[Conflicts.status],
            createdAt = conflict[Conflicts.createdAt].toString(),
            myResolutionSubmitted = myResolution != null,
            partnerResolutionSubmitted = partnerHasResolution,
            summaryAvailable = hasSummary
        )
    }
}
