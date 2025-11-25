package me.pavekovt.facade

import me.pavekovt.dto.PartnerInviteRequest
import me.pavekovt.dto.PartnershipDTO
import me.pavekovt.dto.PartnershipInvitationsDTO
import me.pavekovt.exception.NotFoundException
import me.pavekovt.exception.PartnerShipAlreadyExistsException
import me.pavekovt.repository.PartnershipContextRepository
import me.pavekovt.service.PartnershipService
import me.pavekovt.service.UserService
import java.util.UUID

/**
 * Facade for partnership operations.
 * Orchestrates partnership workflow and validation logic.
 * Initializes partnership context with user profiles when accepted.
 */
class PartnershipFacade(
    private val partnershipService: PartnershipService,
    private val userService: UserService,
    private val partnershipContextRepository: PartnershipContextRepository,
    private val conflictRepository: me.pavekovt.repository.ConflictRepository,
    private val retrospectiveRepository: me.pavekovt.repository.RetrospectiveRepository,
    private val decisionRepository: me.pavekovt.repository.DecisionRepository
) {

    suspend fun sendInvitation(request: PartnerInviteRequest, currentUserId: UUID): PartnershipDTO {
        // Find partner by email
        val partner = userService.findByEmail(request.partnerEmail)
            ?: throw NotFoundException()

        val partnerId = UUID.fromString(partner.id)

        // Cannot invite yourself
        if (partnerId == currentUserId) {
            throw IllegalStateException("Cannot invite yourself as partner")
        }

        // Check if user already has an active partnership
        val existingPartnership = partnershipService.findActivePartnership(currentUserId)
        if (existingPartnership != null) {
            throw PartnerShipAlreadyExistsException()
        }

        // Check if partner already has an active partnership
        val partnerExistingPartnership = partnershipService.findActivePartnership(partnerId)
        if (partnerExistingPartnership != null) {
            throw PartnerShipAlreadyExistsException()
        }

        // Create partnership invitation
        return partnershipService.createInvitation(currentUserId, partnerId)
    }

    suspend fun getInvitations(currentUserId: UUID): PartnershipInvitationsDTO {
        val sent = partnershipService.findPendingInvitationsSent(currentUserId)
        val received = partnershipService.findPendingInvitationsReceived(currentUserId)
        return PartnershipInvitationsDTO(sent, received)
    }

    suspend fun acceptInvitation(partnershipId: UUID, currentUserId: UUID): PartnershipDTO {
        // Check if user already has an active partnership
        val existingPartnership = partnershipService.findActivePartnership(currentUserId)
        if (existingPartnership != null) {
            throw PartnerShipAlreadyExistsException()
        }

        val success = partnershipService.acceptInvitation(partnershipId, currentUserId)
        if (!success) {
            throw NotFoundException()
        }

        val partnership = partnershipService.findById(partnershipId, currentUserId)
            ?: throw IllegalStateException("Partnership not found after acceptance")

        // Initialize partnership context with user profiles
        val user1Id = currentUserId
        val user2Id = UUID.fromString(partnership.partnerId)

        val user1 = userService.findById(user1Id)
            ?: throw IllegalStateException("Current user not found")
        val user2 = userService.findById(user2Id)
            ?: throw IllegalStateException("Partner not found")

        val initialContext = buildString {
            appendLine("PARTNERSHIP SESSION NOTES")
            appendLine("Partners: ${user1.name} & ${user2.name}")
            appendLine()
            appendLine("${user1.name}: ${buildUserDescription(user1)}")
            appendLine("${user2.name}: ${buildUserDescription(user2)}")
            appendLine()
            appendLine("Partnership initiated. Ready to support their journey together.")
        }

        partnershipContextRepository.upsertContext(
            partnershipId = partnershipId,
            compactedSummary = initialContext,
            incrementConflictCount = false
        )

        return partnership
    }

    private fun buildUserDescription(user: me.pavekovt.dto.UserDTO): String {
        val parts = mutableListOf<String>()
        if (user.age != null) parts.add("Age ${user.age}")
        if (user.gender != null) parts.add(user.gender)
        if (user.description != null) parts.add(user.description)
        return if (parts.isEmpty()) "No profile information" else parts.joinToString(", ")
    }

    suspend fun rejectInvitation(partnershipId: UUID, currentUserId: UUID) {
        val success = partnershipService.rejectInvitation(partnershipId, currentUserId)
        if (!success) {
            throw NotFoundException()
        }
    }

    suspend fun getCurrentPartnership(currentUserId: UUID): PartnershipDTO? {
        return partnershipService.findActivePartnership(currentUserId)
    }

    suspend fun endPartnership(currentUserId: UUID) {
        partnershipService.endPartnership(currentUserId)
    }

    suspend fun getHealth(currentUserId: UUID): me.pavekovt.dto.PartnershipHealthDTO {
        val partnership = partnershipService.findActivePartnership(currentUserId)

        if (partnership == null) {
            return me.pavekovt.dto.PartnershipHealthDTO(
                isActive = false,
                partnerName = null,
                needsAttention = listOf("No active partnership"),
                suggestions = listOf("Invite your partner to start using the app together"),
                stats = me.pavekovt.dto.PartnershipStats(
                    totalConflictsResolved = 0,
                    retrospectivesCompleted = 0,
                    activeConflicts = 0,
                    daysSinceLastRetrospective = null,
                    unreviewedDecisions = 0
                )
            )
        }

        val partnerId = UUID.fromString(partnership.partnerId)
        val partner = userService.findById(partnerId)

        // Get partner IDs for queries
        val partnerIds = listOf(partnerId)

        // Get conflict stats
        val allConflicts = conflictRepository.findByUser(currentUserId, partnerIds)
        val resolvedConflicts = allConflicts.count {
            it.status == me.pavekovt.entity.ConflictStatus.APPROVED
        }
        val activeConflicts = allConflicts.count {
            it.status !in listOf(
                me.pavekovt.entity.ConflictStatus.APPROVED,
                me.pavekovt.entity.ConflictStatus.ARCHIVED
            )
        }

        // Get retrospective stats
        val retrospectives = retrospectiveRepository.findByUser(currentUserId)
        val completedRetros = retrospectives.count {
            it.status == me.pavekovt.entity.RetroStatus.COMPLETED.name.lowercase()
        }

        // Get decision stats
        val decisions = decisionRepository.findAll(null)
        val unreviewedDecisions = decisions.count { it.status == "active" }

        // Build needs attention list
        val needsAttention = mutableListOf<String>()
        if (activeConflicts > 0) {
            needsAttention.add("$activeConflicts active ${if (activeConflicts == 1) "conflict" else "conflicts"}")
        }
        if (unreviewedDecisions > 0) {
            needsAttention.add("$unreviewedDecisions unreviewed ${if (unreviewedDecisions == 1) "decision" else "decisions"}")
        }

        // Build suggestions
        val suggestions = mutableListOf<String>()
        if (activeConflicts == 0 && completedRetros == 0) {
            suggestions.add("Start by creating notes about things you'd like to discuss")
        }
        if (completedRetros == 0) {
            suggestions.add("Schedule your first retrospective to review notes together")
        }
        if (unreviewedDecisions > 3) {
            suggestions.add("Review your decision backlog to stay aligned")
        }

        return me.pavekovt.dto.PartnershipHealthDTO(
            isActive = true,
            partnerName = partner?.name,
            needsAttention = needsAttention.ifEmpty { listOf("Everything looks good!") },
            suggestions = suggestions.ifEmpty { listOf("Keep up the great communication!") },
            stats = me.pavekovt.dto.PartnershipStats(
                totalConflictsResolved = resolvedConflicts,
                retrospectivesCompleted = completedRetros,
                activeConflicts = activeConflicts,
                daysSinceLastRetrospective = null,  // TODO: Calculate from last completed retro
                unreviewedDecisions = unreviewedDecisions
            )
        )
    }
}
