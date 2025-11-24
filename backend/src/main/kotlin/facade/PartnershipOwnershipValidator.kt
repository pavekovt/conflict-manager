package me.pavekovt.facade

import me.pavekovt.repository.PartnershipRepository
import java.util.UUID

/**
 * Default implementation of OwnershipValidator that uses the partnership system.
 * Users have access to their own resources and their partner's resources.
 */
class PartnershipOwnershipValidator(
    private val partnershipRepository: PartnershipRepository
) : OwnershipValidator {

    override suspend fun getPartnerId(userId: UUID): UUID {
        return partnershipRepository.getPartnerId(userId)
            ?: throw IllegalStateException("No active partnership found")
    }

    override suspend fun getPartnerIdOrNull(userId: UUID): UUID? {
        return partnershipRepository.getPartnerId(userId)
    }

    override suspend fun requirePartnership(userId: UUID): UUID {
        return partnershipRepository.getPartnerId(userId)
            ?: throw IllegalStateException("You must have an active partnership to perform this action")
    }

    override suspend fun verifyPartnership(userId1: UUID, userId2: UUID) {
        val arePartners = partnershipRepository.arePartners(userId1, userId2)
        if (!arePartners) {
            throw IllegalStateException("Users are not partners")
        }
    }

    override suspend fun hasAccess(resourceOwnerId: UUID, currentUserId: UUID): Boolean {
        if (resourceOwnerId == currentUserId) {
            return true
        }

        val partnerId = partnershipRepository.getPartnerId(currentUserId) ?: return false
        return resourceOwnerId == partnerId
    }
}
