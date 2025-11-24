package me.pavekovt.facade

import java.util.UUID

/**
 * Interface for validating user ownership and access to resources.
 * Provides pluggable, extendable access control for different resource types.
 */
interface OwnershipValidator {
    /**
     * Get the partner ID for a given user.
     * @throws IllegalStateException if user has no active partnership
     */
    suspend fun getCurrentPartnerId(userId: UUID): UUID

    suspend fun getHistoricalPartnerIds(userId: UUID): List<UUID>

    /**
     * Get the partner ID for a given user, or null if no partnership exists.
     */
    suspend fun getCurrentPartnerIdOrNull(userId: UUID): UUID?

    /**
     * Require that a user has an active partnership.
     * @throws IllegalStateException if user has no active partnership
     */
    suspend fun requirePartnership(userId: UUID): UUID

    /**
     * Verify that two users are partners.
     * @throws IllegalStateException if users are not partners
     */
    suspend fun verifyPartnership(userId1: UUID, userId2: UUID)

    /**
     * Check if a user owns or has access to a resource through partnership.
     * @param resourceOwnerId The ID of the user who owns the resource
     * @param currentUserId The ID of the current user requesting access
     * @return true if user has access, false otherwise
     */
    suspend fun hasAccess(resourceOwnerId: UUID, currentUserId: UUID): Boolean
}
