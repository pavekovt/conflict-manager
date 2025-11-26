package me.pavekovt.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.ConflictFeelingsDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.entity.JobType
import me.pavekovt.repository.*
import java.util.UUID

/**
 * Service for conflict data operations with async AI processing.
 */
class ConflictService(
    private val conflictRepository: ConflictRepository,
    private val conflictFeelingsRepository: ConflictFeelingsRepository,
    private val resolutionRepository: ResolutionRepository,
    private val aiSummaryRepository: AISummaryRepository,
    private val decisionRepository: DecisionRepository,
    private val jobRepository: JobRepository,
    private val jobProcessorService: JobProcessorService
) {

    suspend fun create(userId: UUID): ConflictDTO {
        return conflictRepository.create(userId)
    }

    suspend fun findById(conflictId: UUID, userId: UUID): ConflictDTO? {
        return conflictRepository.findById(conflictId, userId)
    }

    suspend fun findByUser(userId: UUID, partnersIds: List<UUID>): List<ConflictDTO> {
        return conflictRepository.findByUser(userId, partnersIds)
    }

    /**
     * Submit feelings for a conflict. Returns immediately with PROCESSING status.
     * AI processing happens in background via JobProcessorService.
     */
    suspend fun submitFeelings(
        conflictId: UUID,
        userId: UUID,
        feelingsText: String,
        partnershipContext: String? = null
    ): ConflictFeelingsDTO {
        // Create feelings entry with PROCESSING status
        val feelings = conflictFeelingsRepository.create(
            conflictId = conflictId,
            userId = userId,
            feelingsText = feelingsText
        )

        // Create background job for AI processing
        val payload = buildJsonObject {
            put("partnershipContext", partnershipContext)
        }.toString()

        val job = jobRepository.create(
            jobType = JobType.PROCESS_FEELINGS,
            entityId = UUID.fromString(feelings.id),
            payload = payload
        )

        // Queue job for processing
        jobProcessorService.queueJob(UUID.fromString(job.id))

        return feelings
    }

    /**
     * Get all feelings for a user in a specific conflict
     */
    suspend fun getFeelings(conflictId: UUID, userId: UUID): List<ConflictFeelingsDTO> {
        return conflictFeelingsRepository.findByConflictAndUser(conflictId, userId)
    }

    /**
     * Get all feelings for a conflict (both users)
     */
    suspend fun getAllFeelingsForConflict(conflictId: UUID): List<ConflictFeelingsDTO> {
        return conflictFeelingsRepository.findByConflict(conflictId)
    }

    /**
     * Submit resolution. If both resolutions submitted, queue AI summary generation job.
     */
    suspend fun submitResolution(
        conflictId: UUID,
        userId: UUID,
        resolutionText: String
    ): ConflictDTO {
        // Check if user already submitted
        if (resolutionRepository.hasResolution(conflictId, userId)) {
            throw IllegalStateException("You have already submitted a resolution for this conflict")
        }

        // Save resolution
        resolutionRepository.create(conflictId, userId, resolutionText)

        // Check if both resolutions are now submitted
        val bothResolutions = resolutionRepository.getBothResolutions(conflictId)

        if (bothResolutions != null) {
            // Update conflict status to PROCESSING_SUMMARY
            conflictRepository.updateStatus(conflictId, ConflictStatus.PROCESSING_SUMMARY)

            // Create background job for AI summary generation
            val job = jobRepository.create(
                jobType = JobType.GENERATE_SUMMARY,
                entityId = conflictId
            )

            // Queue job for processing
            jobProcessorService.queueJob(UUID.fromString(job.id))
        }

        return conflictRepository.findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found")
    }

    suspend fun getSummary(conflictId: UUID, userId: UUID): AISummaryDTO {
        // Get both users involved in the conflict
        val resolutions = resolutionRepository.findByConflict(conflictId)
        val allUserIds = resolutions.map { UUID.fromString(it.userId) }

        if (allUserIds.size != 2) {
            throw IllegalStateException("Invalid conflict state")
        }

        // Get partner user ID (the other user in the conflict)
        val partnerUserId = allUserIds.first { it != userId }

        // Get summary with proper approval status for this user
        return aiSummaryRepository.findByConflictForUser(conflictId, userId, partnerUserId)
            ?: throw IllegalStateException("Summary not found")
    }

    suspend fun approveSummary(summaryId: UUID, userId: UUID, conflictId: UUID) {
        // Approve by this user
        aiSummaryRepository.approve(summaryId, userId, conflictId)

        // Check if both approved
        if (aiSummaryRepository.isApprovedByBoth(summaryId)) {
            val summary = aiSummaryRepository.findById(summaryId)
                ?: throw IllegalStateException("Summary not found")

            // Create decision
            decisionRepository.create(
                conflictId = UUID.fromString(summary.conflictId),
                summary = summary.summaryText,
                category = null
            )

            // Update conflict to approved
            val conflictUUID = UUID.fromString(summary.conflictId)
            conflictRepository.updateStatus(conflictUUID, ConflictStatus.APPROVED)

            // Queue partnership context update job (async)
            val job = jobRepository.create(
                jobType = JobType.UPDATE_PARTNERSHIP_CONTEXT,
                entityId = conflictUUID
            )
            jobProcessorService.queueJob(UUID.fromString(job.id))
        }
    }

    suspend fun requestRefinement(conflictId: UUID) {
        conflictRepository.updateStatus(conflictId, ConflictStatus.REFINEMENT)
    }

    suspend fun archive(conflictId: UUID) {
        conflictRepository.updateStatus(conflictId, ConflictStatus.ARCHIVED)
    }

    suspend fun getAvailableActions(conflictId: UUID, userId: UUID): me.pavekovt.dto.ConflictActionsDTO {
        val conflict = conflictRepository.findById(conflictId, userId)
            ?: throw IllegalStateException("Conflict not found")

        // Get feelings info
        val myFeelings = conflictFeelingsRepository.findByConflictAndUser(conflictId, userId)
        val myFeelingsProcessed = myFeelings.count {
            it.status == me.pavekovt.entity.ConflictFeelingsStatus.COMPLETED
        }

        val partnerFeelings = conflictFeelingsRepository.findByConflict(conflictId)
            .filter { it.userId != userId.toString() }
        val partnerFeelingsCount = partnerFeelings.size
        val partnerFeelingsProcessed = partnerFeelings.count {
            it.status == me.pavekovt.entity.ConflictFeelingsStatus.COMPLETED
        }

        // Get resolution info
        val myResolution = resolutionRepository.hasResolution(conflictId, userId)
        val resolutions = resolutionRepository.findByConflict(conflictId)
        val partnerResolution = resolutions.any { it.userId != userId.toString() }

        // Get summary approval info
        val summary = if (conflict.summaryAvailable) {
            val resolutionsList = resolutionRepository.findByConflict(conflictId)
            val partnerUserId = resolutionsList.firstOrNull { it.userId != userId.toString() }?.let {
                UUID.fromString(it.userId)
            }
            if (partnerUserId != null) {
                aiSummaryRepository.findByConflictForUser(conflictId, userId, partnerUserId)
            } else {
                aiSummaryRepository.findByConflict(conflictId)
            }
        } else null

        val mySummaryApproval = summary?.approvedByMe ?: false

        // Build available actions list
        val actions = mutableListOf<me.pavekovt.dto.ActionAvailability>()

        // Determine what actions are available
        when (conflict.status) {
            ConflictStatus.PENDING_FEELINGS, ConflictStatus.PROCESSING_FEELINGS -> {
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.SUBMIT_FEELINGS,
                        enabled = true,
                        reason = null
                    )
                )
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.VIEW_FEELINGS,
                        enabled = myFeelings.isNotEmpty(),
                        reason = if (myFeelings.isEmpty()) "You haven't submitted any feelings yet" else null
                    )
                )
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.SUBMIT_RESOLUTION,
                        enabled = false,
                        reason = "You must submit and process feelings first"
                    )
                )
            }
            ConflictStatus.PENDING_RESOLUTIONS -> {
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.VIEW_FEELINGS,
                        enabled = true,
                        reason = null
                    )
                )
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.SUBMIT_RESOLUTION,
                        enabled = !myResolution,
                        reason = if (myResolution) "You've already submitted your resolution" else null
                    )
                )
            }
            ConflictStatus.PROCESSING_SUMMARY -> {
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.VIEW_SUMMARY,
                        enabled = false,
                        reason = "AI is still generating the summary"
                    )
                )
            }
            ConflictStatus.SUMMARY_GENERATED, ConflictStatus.REFINEMENT -> {
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.VIEW_SUMMARY,
                        enabled = true,
                        reason = null
                    )
                )
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.APPROVE_SUMMARY,
                        enabled = !mySummaryApproval,
                        reason = if (mySummaryApproval) "You've already approved the summary" else null
                    )
                )
                actions.add(
                    me.pavekovt.dto.ActionAvailability(
                        action = me.pavekovt.dto.ConflictAction.REQUEST_REFINEMENT,
                        enabled = true,
                        reason = null
                    )
                )
            }
            else -> {
                // No actions available for APPROVED or ARCHIVED
            }
        }

        // Archive is always available unless already archived
        if (conflict.status != ConflictStatus.ARCHIVED) {
            actions.add(
                me.pavekovt.dto.ActionAvailability(
                    action = me.pavekovt.dto.ConflictAction.ARCHIVE,
                    enabled = true,
                    reason = null
                )
            )
        }

        // Build progress info
        val progress = me.pavekovt.dto.ConflictProgress(
            myProgress = me.pavekovt.dto.UserProgress(
                feelingsSubmitted = myFeelings.size,
                feelingsProcessed = myFeelingsProcessed,
                resolutionSubmitted = myResolution,
                summaryApproved = mySummaryApproval
            ),
            partnerProgress = me.pavekovt.dto.UserProgress(
                feelingsSubmitted = partnerFeelingsCount,
                feelingsProcessed = partnerFeelingsProcessed,
                resolutionSubmitted = partnerResolution,
                summaryApproved = false  // TODO: Get partner approval status
            )
        )

        // Build next steps
        val nextSteps = mutableListOf<String>()
        when (conflict.status) {
            ConflictStatus.PENDING_FEELINGS -> {
                if (myFeelings.isEmpty()) {
                    nextSteps.add("Submit your feelings about this conflict")
                } else if (myFeelingsProcessed < myFeelings.size) {
                    nextSteps.add("Wait for AI to process your feelings")
                }
                if (partnerFeelingsCount == 0) {
                    nextSteps.add("Your partner needs to submit their feelings")
                }
            }
            ConflictStatus.PENDING_RESOLUTIONS -> {
                if (!myResolution) {
                    nextSteps.add("Review AI guidance and submit your resolution")
                } else if (!partnerResolution) {
                    nextSteps.add("Wait for your partner to submit their resolution")
                }
            }
            ConflictStatus.PROCESSING_SUMMARY -> {
                nextSteps.add("Wait for AI to generate the summary")
            }
            ConflictStatus.SUMMARY_GENERATED, ConflictStatus.REFINEMENT -> {
                if (!mySummaryApproval) {
                    nextSteps.add("Review and approve the AI-generated summary")
                } else {
                    nextSteps.add("Wait for your partner to approve the summary")
                }
            }
            ConflictStatus.APPROVED -> {
                nextSteps.add("Conflict resolved! Check your decision backlog")
            }
            else -> {}
        }

        return me.pavekovt.dto.ConflictActionsDTO(
            availableActions = actions,
            currentPhase = conflict.status.name,
            progress = progress,
            nextSteps = nextSteps
        )
    }
}
