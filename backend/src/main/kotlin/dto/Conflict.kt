package me.pavekovt.dto

import kotlinx.serialization.Serializable
import me.pavekovt.entity.ConflictStatus

@Serializable
data class ConflictDTO(
    val id: String,
    val initiatedBy: String,
    val status: ConflictStatus,
    val createdAt: String,
    val myResolutionSubmitted: Boolean,
    val partnerResolutionSubmitted: Boolean,
    val summaryAvailable: Boolean
)
