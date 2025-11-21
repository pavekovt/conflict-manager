package me.pavekovt.repository

import me.pavekovt.dto.AISummaryDTO
import java.util.UUID

interface AISummaryRepository {
    suspend fun create(conflictId: UUID, summaryText: String, provider: String): UUID
    suspend fun findByConflict(conflictId: UUID): AISummaryDTO?
    suspend fun findById(summaryId: UUID): AISummaryDTO?
    suspend fun approve(summaryId: UUID, userId: UUID, conflictId: UUID): Boolean
    suspend fun isApprovedByBoth(summaryId: UUID): Boolean
}
