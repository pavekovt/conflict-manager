package me.pavekovt.utils

import me.pavekovt.dto.ConflictAction
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.entity.ConflictStatus
import java.util.UUID

/**
 * Enhances ConflictDTO with UX-friendly state information
 */
object ConflictDTOEnhancer {

    fun enhance(
        conflict: ConflictDTO,
        currentUserId: UUID,
        myFeelingsCount: Int = 0,
        partnerFeelingsCount: Int = 0,
        myFeelingsProcessed: Int = 0,
        partnerFeelingsProcessed: Int = 0
    ): ConflictDTO {
        val nextAction = determineNextAction(
            conflict,
            currentUserId,
            myFeelingsCount,
            partnerFeelingsCount,
            myFeelingsProcessed,
            partnerFeelingsProcessed
        )
        val waitingFor = determineWaitingFor(
            conflict,
            currentUserId,
            myFeelingsCount,
            partnerFeelingsCount,
            myFeelingsProcessed,
            partnerFeelingsProcessed
        )
        val allowedActions = determineAllowedActions(
            conflict,
            currentUserId,
            myFeelingsCount,
            myFeelingsProcessed
        )

        return conflict.copy(
            nextAction = nextAction,
            waitingFor = waitingFor,
            allowedActions = allowedActions
        )
    }

    private fun determineNextAction(
        conflict: ConflictDTO,
        currentUserId: UUID,
        myFeelingsCount: Int,
        partnerFeelingsCount: Int,
        myFeelingsProcessed: Int,
        partnerFeelingsProcessed: Int
    ): String? {
        return when (conflict.status) {
            ConflictStatus.PENDING_FEELINGS -> {
                if (myFeelingsCount == 0) {
                    "Submit your feelings about this conflict"
                } else if (myFeelingsProcessed < myFeelingsCount) {
                    "Wait for AI to process your feelings"
                } else if (partnerFeelingsCount == 0) {
                    "Wait for your partner to submit their feelings"
                } else {
                    "You can submit more feelings or wait for your partner"
                }
            }
            ConflictStatus.PROCESSING_FEELINGS -> {
                "AI is processing feelings - please wait"
            }
            ConflictStatus.PENDING_RESOLUTIONS -> {
                if (!conflict.myResolutionSubmitted) {
                    "Submit your resolution based on AI guidance"
                } else {
                    "Wait for your partner to submit their resolution"
                }
            }
            ConflictStatus.PROCESSING_SUMMARY -> {
                "AI is generating summary - please wait"
            }
            ConflictStatus.SUMMARY_GENERATED -> {
                "Review and approve the AI-generated summary"
            }
            ConflictStatus.REFINEMENT -> {
                "AI is refining the summary based on your feedback"
            }
            ConflictStatus.APPROVED -> {
                "Conflict resolved! Decision has been added to your backlog"
            }
            ConflictStatus.ARCHIVED -> {
                null
            }
        }
    }

    private fun determineWaitingFor(
        conflict: ConflictDTO,
        currentUserId: UUID,
        myFeelingsCount: Int,
        partnerFeelingsCount: Int,
        myFeelingsProcessed: Int,
        partnerFeelingsProcessed: Int
    ): String? {
        return when (conflict.status) {
            ConflictStatus.PENDING_FEELINGS -> {
                when {
                    myFeelingsCount > 0 && myFeelingsProcessed < myFeelingsCount -> "AI processing your feelings"
                    myFeelingsCount == 0 -> null
                    partnerFeelingsCount == 0 -> "Partner to submit feelings"
                    partnerFeelingsProcessed < partnerFeelingsCount -> "AI processing partner's feelings"
                    else -> null
                }
            }
            ConflictStatus.PROCESSING_FEELINGS -> "AI processing"
            ConflictStatus.PENDING_RESOLUTIONS -> {
                if (conflict.myResolutionSubmitted && !conflict.partnerResolutionSubmitted) {
                    "Partner's resolution"
                } else {
                    null
                }
            }
            ConflictStatus.PROCESSING_SUMMARY -> "AI summary generation"
            ConflictStatus.SUMMARY_GENERATED -> {
                if (conflict.summaryAvailable) {
                    "Your approval or partner's approval"
                } else {
                    "AI summary"
                }
            }
            ConflictStatus.REFINEMENT -> "AI refinement"
            ConflictStatus.APPROVED, ConflictStatus.ARCHIVED -> null
        }
    }

    private fun determineAllowedActions(
        conflict: ConflictDTO,
        currentUserId: UUID,
        myFeelingsCount: Int,
        myFeelingsProcessed: Int
    ): List<ConflictAction> {
        val actions = mutableListOf<ConflictAction>()

        when (conflict.status) {
            ConflictStatus.PENDING_FEELINGS, ConflictStatus.PROCESSING_FEELINGS -> {
                actions.add(ConflictAction.SUBMIT_FEELINGS)
                if (myFeelingsCount > 0) {
                    actions.add(ConflictAction.VIEW_FEELINGS)
                }
            }
            ConflictStatus.PENDING_RESOLUTIONS -> {
                actions.add(ConflictAction.VIEW_FEELINGS)
                if (!conflict.myResolutionSubmitted) {
                    actions.add(ConflictAction.SUBMIT_RESOLUTION)
                }
            }
            ConflictStatus.PROCESSING_SUMMARY -> {
                // Wait for AI, no actions available
            }
            ConflictStatus.SUMMARY_GENERATED, ConflictStatus.REFINEMENT -> {
                actions.add(ConflictAction.VIEW_SUMMARY)
                actions.add(ConflictAction.APPROVE_SUMMARY)
                actions.add(ConflictAction.REQUEST_REFINEMENT)
            }
            ConflictStatus.APPROVED, ConflictStatus.ARCHIVED -> {
                // Conflict complete, no actions
            }
        }

        // Archive is always available (unless already archived)
        if (conflict.status != ConflictStatus.ARCHIVED) {
            actions.add(ConflictAction.ARCHIVE)
        }

        return actions
    }
}
