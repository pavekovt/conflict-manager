package me.pavekovt.service.job

import me.pavekovt.ai.UserProfile
import me.pavekovt.repository.UserRepository
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Helper service for loading user profiles for AI context
 */
class UserProfileLoader(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(UserProfileLoader::class.java)

    /**
     * Load a user profile by ID
     */
    suspend fun loadProfile(userId: UUID): UserProfile {
        logger.debug("Loading profile for user {}", userId)
        val user = userRepository.findById(userId)
            ?: throw IllegalStateException("User $userId not found")

        return UserProfile(
            name = user.name,
            age = user.age,
            gender = user.gender,
            description = user.description
        )
    }

    /**
     * Load profiles for two users (both partners)
     */
    suspend fun loadProfiles(user1Id: UUID, user2Id: UUID): Pair<UserProfile, UserProfile> {
        logger.debug("Loading profiles for users {} and {}", user1Id, user2Id)
        return Pair(
            loadProfile(user1Id),
            loadProfile(user2Id)
        )
    }
}
