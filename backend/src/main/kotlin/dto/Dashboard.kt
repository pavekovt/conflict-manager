package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class DashboardDTO(
    val pendingActions: List<PendingAction>,
    val summary: DashboardSummary,
    val partnerActivity: PartnerActivity?
)

@Serializable
data class PendingAction(
    val type: String,  // "CONFLICT", "RETROSPECTIVE", "DECISION"
    val id: String,
    val title: String,
    val action: String,
    val priority: ActionPriority,
    val dueDate: String? = null,
    val url: String
)

@Serializable
enum class ActionPriority {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
data class DashboardSummary(
    val totalConflicts: Int,
    val activeConflicts: Int,
    val conflictsNeedingMyAction: Int,
    val conflictsAwaitingPartner: Int,
    val pendingRetrospectives: Int,
    val unreviewedDecisions: Int,
    val draftJournals: Int,
    val completedJournalsUnprocessed: Int
)

@Serializable
data class PartnerActivity(
    val partnerName: String,
    val lastActive: String?,
    val currentlyActive: Boolean,
    val recentActions: List<RecentAction>
)

@Serializable
data class RecentAction(
    val action: String,
    val resource: String,
    val timestamp: String
)
