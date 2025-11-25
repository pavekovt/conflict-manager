package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConflictActionsDTO(
    val availableActions: List<ActionAvailability>,
    val currentPhase: String,
    val progress: ConflictProgress,
    val nextSteps: List<String>
)

@Serializable
data class ActionAvailability(
    val action: ConflictAction,
    val enabled: Boolean,
    val reason: String?
)

@Serializable
data class ConflictProgress(
    val myProgress: UserProgress,
    val partnerProgress: UserProgress
)

@Serializable
data class UserProgress(
    val feelingsSubmitted: Int,
    val feelingsProcessed: Int,
    val resolutionSubmitted: Boolean,
    val summaryApproved: Boolean
)
