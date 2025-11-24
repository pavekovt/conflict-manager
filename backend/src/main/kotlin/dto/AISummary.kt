package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class AISummaryDTO(
    val id: String,
    val conflictId: String,
    val summaryText: String,
    val patterns: String?,
    val advice: String?,
    val recurringIssues: List<String>,
    val themeTags: List<String>,
    val provider: String,
    val approvedByMe: Boolean,
    val approvedByPartner: Boolean,
    val createdAt: String
)
