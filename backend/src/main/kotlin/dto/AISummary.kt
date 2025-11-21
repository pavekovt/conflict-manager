package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class AISummaryDTO(
    val id: String,
    val conflictId: String,
    val summaryText: String,
    val provider: String,
    val approvedByMe: Boolean,
    val approvedByPartner: Boolean,
    val createdAt: String
)
