package me.pavekovt.repository

import me.pavekovt.dto.ResolutionDTO
import java.util.UUID

interface ResolutionRepository {
    suspend fun create(conflictId: UUID, userId: UUID, resolutionText: String): ResolutionDTO
    suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): ResolutionDTO?
    suspend fun findByConflict(conflictId: UUID): List<ResolutionDTO>
    suspend fun hasResolution(conflictId: UUID, userId: UUID): Boolean
    suspend fun getBothResolutions(conflictId: UUID): Pair<String, String>?
}
