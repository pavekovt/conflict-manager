package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResolutionDTO(
    val id: String,
    val conflictId: String,
    val userId: String,
    val resolutionText: String,
    val submittedAt: String
)
