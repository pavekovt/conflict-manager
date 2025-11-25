package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class PartnershipHealthDTO(
    val isActive: Boolean,
    val partnerName: String?,
    val needsAttention: List<String>,
    val suggestions: List<String>,
    val stats: PartnershipStats
)

@Serializable
data class PartnershipStats(
    val totalConflictsResolved: Int,
    val retrospectivesCompleted: Int,
    val activeConflicts: Int,
    val daysSinceLastRetrospective: Int?,
    val unreviewedDecisions: Int
)
