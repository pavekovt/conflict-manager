package me.pavekovt.repository

import me.pavekovt.dto.ConflictFeelingsDTO
import java.util.UUID

interface ConflictFeelingsRepository {
    /**
     * Create a new feelings entry for a conflict
     */
    suspend fun create(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String,
        aiGuidance: String,
        suggestedResolution: String
    ): ConflictFeelingsDTO

    /**
     * Find feelings entry by conflict and user
     */
    suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): ConflictFeelingsDTO?

    /**
     * Check if user has submitted feelings for a conflict
     */
    suspend fun hasSubmittedFeelings(conflictId: UUID, userId: UUID): Boolean

    /**
     * Get both users' feelings for a conflict (returns list of 0-2 entries)
     */
    suspend fun findByConflict(conflictId: UUID): List<ConflictFeelingsDTO>

    /**
     * Count how many users have submitted feelings for a conflict
     */
    suspend fun countSubmittedFeelings(conflictId: UUID): Int
}
