package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.ResolutionDTO
import me.pavekovt.entity.Resolutions
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class ResolutionRepositoryImpl : ResolutionRepository {

    override suspend fun create(conflictId: UUID, userId: UUID, resolutionText: String): ResolutionDTO = dbQuery {
        Resolutions.insertReturning {
            it[Resolutions.conflictId] = conflictId
            it[Resolutions.userId] = userId
            it[Resolutions.resolutionText] = resolutionText
        }
            .single()
            .toResolutionDTO()
    }

    override suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): ResolutionDTO? = dbQuery {
        Resolutions.selectAll()
            .where { (Resolutions.conflictId eq conflictId) and (Resolutions.userId eq userId) }
            .singleOrNull()
            ?.toResolutionDTO()
    }

    override suspend fun findByConflict(conflictId: UUID): List<ResolutionDTO> = dbQuery {
        Resolutions.selectAll()
            .where { Resolutions.conflictId eq conflictId }
            .map { it.toResolutionDTO() }
    }

    override suspend fun hasResolution(conflictId: UUID, userId: UUID): Boolean = dbQuery {
        Resolutions.selectAll()
            .where { (Resolutions.conflictId eq conflictId) and (Resolutions.userId eq userId) }
            .count() > 0
    }

    override suspend fun getBothResolutions(conflictId: UUID): Pair<String, String>? = dbQuery {
        val resolutions = Resolutions
            .selectAll()
            .where { Resolutions.conflictId eq conflictId }
            .map { it[Resolutions.resolutionText] }

        if (resolutions.size == 2) {
            Pair(resolutions[0], resolutions[1])
        } else null
    }
}

private fun ResultRow.toResolutionDTO() = ResolutionDTO(
    id = this[Resolutions.id].value.toString(),
    conflictId = this[Resolutions.conflictId].value.toString(),
    userId = this[Resolutions.userId].value.toString(),
    resolutionText = this[Resolutions.resolutionText],
    submittedAt = this[Resolutions.submittedAt].toString()
)
