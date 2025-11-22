package me.pavekovt.service

import me.pavekovt.dto.PartnerInviteRequest
import me.pavekovt.dto.PartnershipDTO
import me.pavekovt.dto.PartnershipInvitationsDTO
import me.pavekovt.exception.NotFoundException
import me.pavekovt.repository.PartnershipRepository
import me.pavekovt.repository.UserRepository
import java.util.UUID

class PartnershipService(
    private val partnershipRepository: PartnershipRepository,
    private val userRepository: UserRepository
) {
    suspend fun sendInvitation(request: PartnerInviteRequest, currentUserId: UUID): PartnershipDTO {
        // Find partner by email
        val partner = userRepository.findByEmail(request.partnerEmail)
            ?: throw IllegalStateException("User with email ${request.partnerEmail} not found")

        val partnerId = UUID.fromString(partner.id)

        // Cannot invite yourself
        if (partnerId == currentUserId) {
            throw IllegalStateException("Cannot invite yourself as partner")
        }

        // Check if user already has an active partnership
        val existingPartnership = partnershipRepository.findActivePartnership(currentUserId)
        if (existingPartnership != null) {
            throw IllegalStateException("You already have an active partnership")
        }

        // Check if partner already has an active partnership
        val partnerExistingPartnership = partnershipRepository.findActivePartnership(partnerId)
        if (partnerExistingPartnership != null) {
            throw IllegalStateException("Partner already has an active partnership")
        }

        // Create partnership invitation
        val partnershipId = partnershipRepository.create(currentUserId, partnerId, currentUserId)

        return partnershipRepository.findById(partnershipId)
            ?: throw IllegalStateException("Failed to create partnership")
    }

    suspend fun getInvitations(currentUserId: UUID): PartnershipInvitationsDTO {
        val sent = partnershipRepository.findPendingInvitationsSent(currentUserId)
        val received = partnershipRepository.findPendingInvitationsReceived(currentUserId)
        return PartnershipInvitationsDTO(sent, received)
    }

    suspend fun acceptInvitation(partnershipId: UUID, currentUserId: UUID): PartnershipDTO {
        // Check if user already has an active partnership
        val existingPartnership = partnershipRepository.findActivePartnership(currentUserId)
        if (existingPartnership != null) {
            throw IllegalStateException("You already have an active partnership")
        }

        val success = partnershipRepository.accept(partnershipId, currentUserId)
        if (!success) {
            throw IllegalStateException("Cannot accept this partnership invitation")
        }

        return partnershipRepository.findById(partnershipId)
            ?: throw IllegalStateException("Partnership not found after acceptance")
    }

    suspend fun rejectInvitation(partnershipId: UUID, currentUserId: UUID) {
        val success = partnershipRepository.reject(partnershipId, currentUserId)
        if (!success) {
            throw IllegalStateException("Cannot reject this partnership invitation")
        }
    }

    suspend fun getCurrentPartnership(currentUserId: UUID): PartnershipDTO? {
        return partnershipRepository.findActivePartnership(currentUserId)
    }

    suspend fun endPartnership(currentUserId: UUID) {
        val success = partnershipRepository.end(currentUserId)
        if (!success) {
            throw NotFoundException()
        }
    }

    suspend fun getPartnerId(currentUserId: UUID): UUID {
        return partnershipRepository.getPartnerId(currentUserId)
            ?: throw IllegalStateException("No active partnership found")
    }

    suspend fun requirePartnership(currentUserId: UUID): UUID {
        return partnershipRepository.getPartnerId(currentUserId)
            ?: throw IllegalStateException("You must have an active partnership to perform this action")
    }

    suspend fun verifyPartnership(userId1: UUID, userId2: UUID) {
        val arePartners = partnershipRepository.arePartners(userId1, userId2)
        if (!arePartners) {
            throw IllegalStateException("Users are not partners")
        }
    }
}
