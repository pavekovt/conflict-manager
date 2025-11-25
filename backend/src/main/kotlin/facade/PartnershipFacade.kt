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
    private val partnershipContextRepository: PartnershipContextRepository
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
}
