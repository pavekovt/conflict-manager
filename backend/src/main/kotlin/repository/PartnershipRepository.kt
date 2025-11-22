package me.pavekovt.repository

import me.pavekovt.dto.PartnershipDTO
import java.util.UUID

interface PartnershipRepository {
    suspend fun create(userId1: UUID, userId2: UUID, initiatedBy: UUID): UUID
    suspend fun findById(partnershipId: UUID): PartnershipDTO?
    suspend fun findActivePartnership(userId: UUID): PartnershipDTO?
    suspend fun findPendingInvitationsSent(userId: UUID): List<PartnershipDTO>
    suspend fun findPendingInvitationsReceived(userId: UUID): List<PartnershipDTO>
    suspend fun accept(partnershipId: UUID, userId: UUID): Boolean
    suspend fun reject(partnershipId: UUID, userId: UUID): Boolean
    suspend fun end(userId: UUID): Boolean
    suspend fun getPartnerId(userId: UUID): UUID?
    suspend fun arePartners(userId1: UUID, userId2: UUID): Boolean
}
