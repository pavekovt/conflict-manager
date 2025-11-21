package me.pavekovt.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConflictDTO(
    val id: String,
    val initiatedBy: String,
    val status: String,
    val createdAt: String,
    val myResolutionSubmitted: Boolean,
    val partnerResolutionSubmitted: Boolean,
    val summaryAvailable: Boolean
)
