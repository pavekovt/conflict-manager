package me.pavekovt.repository

import me.pavekovt.dto.ConflictFeelingsDTO
import java.util.UUID

interface ConflictFeelingsRepository {
    /**
     * Create a new feelings entry for a conflict (without AI response - will be filled by background job)
     */
    suspend fun create(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String
    ): ConflictFeelingsDTO

    /**
     * Find feelings entry by ID
     */
    suspend fun findById(feelingId: UUID): ConflictFeelingsDTO?

    /**
     * Update feelings entry with AI response after processing
     */
    suspend fun updateWithAIResponse(
        feelingId: UUID,
        aiGuidance: String,
        suggestedResolution: String,
        emotionalTone: String
    ): ConflictFeelingsDTO?

    /**
     * Find ALL feelings entries by conflict and user (users can submit multiple feelings)
     */
    suspend fun findByConflictAndUser(conflictId: UUID, userId: UUID): List<ConflictFeelingsDTO>

    /**
     * Get all feelings for a conflict (from both users)
     */
    suspend fun findByConflict(conflictId: UUID): List<ConflictFeelingsDTO>

    /**
     * Count how many COMPLETED feelings both users have submitted
     */
    suspend fun countCompletedFeelings(conflictId: UUID): Int
}
