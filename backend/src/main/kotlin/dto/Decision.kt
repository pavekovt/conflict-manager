package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class DecisionDTO(
    val id: String,
    val conflictId: String?,
    val summary: String,
    val category: String?,
    val status: String,
    val createdAt: String,
    val reviewedAt: String?
)
