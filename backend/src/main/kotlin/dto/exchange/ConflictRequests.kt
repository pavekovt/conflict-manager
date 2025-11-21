package me.pavekovt.dto.exchange

data class CreateConflictRequest(
    val title: String? = null
)

data class SubmitResolutionRequest(
    val resolutionText: String
)

data class ApproveConflictRequest(
    val approved: Boolean
)
