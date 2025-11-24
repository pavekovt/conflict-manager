package me.pavekovt.repository

import java.util.UUID

interface PartnershipContextRepository {
    /**
     * Get the current compacted context summary for a partnership
     */
    suspend fun getContext(partnershipId: UUID): PartnershipContextData?

    /**
     * Create or update the partnership context with new summary
     */
    suspend fun upsertContext(
        partnershipId: UUID,
        compactedSummary: String,
        incrementConflictCount: Boolean = false,
        incrementRetroCount: Boolean = false
    ): UUID

    /**
     * Check if context exists for a partnership
     */
    suspend fun exists(partnershipId: UUID): Boolean
}

data class PartnershipContextData(
    val id: UUID,
    val partnershipId: UUID,
    val compactedSummary: String,
    val conflictCount: Int,
    val retroCount: Int
)
