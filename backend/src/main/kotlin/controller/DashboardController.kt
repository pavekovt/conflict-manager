package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.*
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.entity.JournalStatus
import me.pavekovt.entity.RetroStatus
import me.pavekovt.repository.*
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.dashboardRouting() {
    val conflictRepository by inject<ConflictRepository>()
    val retrospectiveRepository by inject<RetrospectiveRepository>()
    val decisionRepository by inject<DecisionRepository>()
    val journalRepository by inject<JournalRepository>()
    val partnershipRepository by inject<PartnershipRepository>()
    val userRepository by inject<UserRepository>()

    authenticate("jwt") {
        route("/dashboard") {
            get {
                val userId = call.getCurrentUserId()

                // Get partner IDs for conflict queries
                val partnership = partnershipRepository.findActivePartnership(userId)
                val partnerId = partnership?.let { UUID.fromString(it.partnerId) }
                val partnerIds = listOfNotNull(partnerId)

                // Get conflicts
                val allConflicts = conflictRepository.findByUser(userId, partnerIds)
                val activeConflicts = allConflicts.filter {
                    it.status !in listOf(ConflictStatus.APPROVED, ConflictStatus.ARCHIVED)
                }

                // Determine which conflicts need my action
                val needsMyAction = activeConflicts.count { conflict ->
                    when (conflict.status) {
                        ConflictStatus.PENDING_FEELINGS -> true
                        ConflictStatus.PENDING_RESOLUTIONS -> !conflict.myResolutionSubmitted
                        ConflictStatus.SUMMARY_GENERATED, ConflictStatus.REFINEMENT -> true
                        else -> false
                    }
                }

                val awaitingPartner = activeConflicts.count { conflict ->
                    when (conflict.status) {
                        ConflictStatus.PENDING_RESOLUTIONS -> conflict.myResolutionSubmitted && !conflict.partnerResolutionSubmitted
                        ConflictStatus.PROCESSING_SUMMARY, ConflictStatus.PROCESSING_FEELINGS -> false
                        else -> false
                    }
                }

                // Get retrospectives
                val retrospectives = retrospectiveRepository.findByUser(userId)
                val pendingRetros = retrospectives.count { retro ->
                    val status = try {
                        RetroStatus.valueOf(retro.status.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                    status in listOf(RetroStatus.SCHEDULED, RetroStatus.IN_PROGRESS, RetroStatus.PENDING_APPROVAL)
                }

                // Get decisions
                val decisions = decisionRepository.findAll(null)
                val unreviewedDecisions = decisions.count { it.status == "active" }

                // Get journals
                val journals = journalRepository.findByUser(userId, null, 1000, 0)
                val draftJournals = journals.count { it.status == JournalStatus.DRAFT.name.lowercase() }
                val completedUnprocessed = journals.count { it.status == JournalStatus.COMPLETED.name.lowercase() }

                // Build pending actions
                val pendingActions = mutableListOf<PendingAction>()

                // Add conflict actions
                activeConflicts.forEach { conflict ->
                    val action = when (conflict.status) {
                        ConflictStatus.PENDING_FEELINGS -> "Submit your feelings"
                        ConflictStatus.PENDING_RESOLUTIONS ->
                            if (!conflict.myResolutionSubmitted) "Submit your resolution" else null
                        ConflictStatus.SUMMARY_GENERATED, ConflictStatus.REFINEMENT -> "Review and approve summary"
                        else -> null
                    }

                    if (action != null) {
                        pendingActions.add(
                            PendingAction(
                                type = "CONFLICT",
                                id = conflict.id,
                                title = "Conflict Resolution",
                                action = action,
                                priority = ActionPriority.HIGH,
                                url = "/api/conflicts/${conflict.id}"
                            )
                        )
                    }
                }

                // Add retrospective actions
                retrospectives.filter { retro ->
                    val status = try {
                        RetroStatus.valueOf(retro.status.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                    status == RetroStatus.PENDING_APPROVAL
                }.forEach { retro ->
                    pendingActions.add(
                        PendingAction(
                            type = "RETROSPECTIVE",
                            id = retro.id,
                            title = "Retrospective Approval",
                            action = "Approve discussion points",
                            priority = ActionPriority.MEDIUM,
                            url = "/api/retrospectives/${retro.id}"
                        )
                    )
                }

                // Add decision review actions
                if (unreviewedDecisions > 0) {
                    pendingActions.add(
                        PendingAction(
                            type = "DECISION",
                            id = "backlog",
                            title = "Decision Backlog",
                            action = "Review $unreviewedDecisions unreviewed decisions",
                            priority = ActionPriority.LOW,
                            url = "/api/decisions"
                        )
                    )
                }

                // Get partner activity (if partnership exists)
                val partnerActivity = if (partnership != null && partnerId != null) {
                    val partner = userRepository.findById(partnerId)
                    if (partner != null) {
                        PartnerActivity(
                            partnerName = partner.name,
                            lastActive = null,  // TODO: Track user activity timestamps
                            currentlyActive = false,
                            recentActions = emptyList()  // TODO: Track partner actions
                        )
                    } else null
                } else null

                val dashboard = DashboardDTO(
                    pendingActions = pendingActions.sortedByDescending {
                        when (it.priority) {
                            ActionPriority.HIGH -> 3
                            ActionPriority.MEDIUM -> 2
                            ActionPriority.LOW -> 1
                        }
                    },
                    summary = DashboardSummary(
                        totalConflicts = allConflicts.size,
                        activeConflicts = activeConflicts.size,
                        conflictsNeedingMyAction = needsMyAction,
                        conflictsAwaitingPartner = awaitingPartner,
                        pendingRetrospectives = pendingRetros,
                        unreviewedDecisions = unreviewedDecisions,
                        draftJournals = draftJournals,
                        completedJournalsUnprocessed = completedUnprocessed
                    ),
                    partnerActivity = partnerActivity
                )

                call.respond(dashboard)
            }
        }
    }
}
