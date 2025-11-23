package me.pavekovt.service

import me.pavekovt.dto.PartnershipDTO
import me.pavekovt.exception.NotFoundException
import me.pavekovt.repository.PartnershipRepository
import java.util.UUID

/**
 * Simple service for partnership data operations.
 * Delegates to repository without complex business logic.
 */
class PartnershipService(
    private val partnershipRepository: PartnershipRepository
) {
    suspend fun createInvitation(userId1: UUID, userId2: UUID): PartnershipDTO {
        val partnershipId = partnershipRepository.create(userId1, userId2, userId1)
        return partnershipRepository.findById(partnershipId)
            ?: throw IllegalStateException("Failed to create partnership")
    }

    suspend fun findById(partnershipId: UUID): PartnershipDTO? {
        return partnershipRepository.findById(partnershipId)
    }

    suspend fun findActivePartnership(userId: UUID): PartnershipDTO? {
        return partnershipRepository.findActivePartnership(userId)
    }

    suspend fun findPendingInvitationsSent(userId: UUID): List<PartnershipDTO> {
        return partnershipRepository.findPendingInvitationsSent(userId)
    }

    suspend fun findPendingInvitationsReceived(userId: UUID): List<PartnershipDTO> {
        return partnershipRepository.findPendingInvitationsReceived(userId)
    }

    suspend fun acceptInvitation(partnershipId: UUID, userId: UUID): Boolean {
        return partnershipRepository.accept(partnershipId, userId)
    }

    suspend fun rejectInvitation(partnershipId: UUID, userId: UUID): Boolean {
        return partnershipRepository.reject(partnershipId, userId)
    }

    suspend fun endPartnership(userId: UUID) {
        val success = partnershipRepository.end(userId)
        if (!success) {
            throw NotFoundException()
        }
    }
}
