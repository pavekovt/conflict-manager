package me.pavekovt.dto.exchange

import kotlinx.serialization.Serializable

@Serializable
data class CreateConflictRequest(
    val title: String? = null
)

@Serializable
data class SubmitFeelingsRequest(
    val feelingsText: String
)

@Serializable
data class SubmitResolutionRequest(
    val resolutionText: String
)

@Serializable
data class ApproveConflictRequest(
    val approved: Boolean
)
