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
    val summaryAvailable: Boolean,
    // UX improvements
    val nextAction: String? = null,
    val waitingFor: String? = null,
    val allowedActions: List<ConflictAction> = emptyList()
)

@Serializable
enum class ConflictAction {
    SUBMIT_FEELINGS,
    VIEW_FEELINGS,
    SUBMIT_RESOLUTION,
    VIEW_SUMMARY,
    APPROVE_SUMMARY,
    REQUEST_REFINEMENT,
    ARCHIVE
}
