package me.pavekovt.integration

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartnershipApiTest : IntegrationTestBase() {

    @Test
    fun `POST invite should send partnership invitation`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()

            // When
            val partnership = user1.sendInvite(user2.email)

            // Then
            assertNotNull(partnership.id)
            assertEquals(user2.email, partnership.partnerEmail)
            assertEquals("pending", partnership.status)
            assertEquals(true, partnership.initiatedByMe)
            assertNull(partnership.acceptedAt)
        }
    }

    @Test
    fun `POST invite should return 400 for invalid email`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val response = user.sendInviteRaw("invalid-email")

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `POST invite should return 404 for non-existent user`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val response = user.sendInviteRaw("nonexistent@example.com")

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `POST invite should return 400 when inviting self`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val response = user.sendInviteRaw(user.email)

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `POST invite should return 409 when user already has active partnership`() = runBlocking {
        utils.run {
            // Given
            val (user1) = registerPartners()
            val user3 = registerUser()

            // When - user1 already has partnership with user2, tries to invite user3
            val response = user1.sendInviteRaw(user3.email)

            // Then
            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }

    @Test
    fun `POST invite should return 409 when pending invitation already exists`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            user1.sendInvite(user2.email)

            // When - send duplicate invitation
            val response = user1.sendInviteRaw(user2.email)

            // Then
            assertEquals(HttpStatusCode.Conflict, response.status)
        }
    }

    @Test
    fun `GET invitations should return sent and received invitations`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            val user3 = registerUser()

            // User1 sends to User2
            user1.sendInvite(user2.email)
            // User3 sends to User1
            user3.sendInvite(user1.email)

            // When
            val invitations = user1.getInvitations()

            // Then - User1 should see both sent and received
            assertEquals(1, invitations.sent.size)
            assertEquals(1, invitations.received.size)

            val sent = invitations.sent.first()
            val received = invitations.received.first()

            assertTrue(sent.initiatedByMe && sent.partnerEmail == user2.email)
            assertTrue(!received.initiatedByMe && received.partnerEmail == user3.email)
        }
    }

    @Test
    fun `GET invitations should return empty list when no invitations`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val invitations = user.getInvitations()

            // Then
            assertTrue(invitations.sent.isEmpty())
            assertTrue(invitations.received.isEmpty())
        }
    }

    @Test
    fun `POST accept should accept invitation and create active partnership`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            val invitation = user1.sendInvite(user2.email)

            // When
            val partnership = user2.acceptInvite(invitation.id)

            // Then
            assertEquals("active", partnership.status)
            assertNotNull(partnership.acceptedAt)
            assertEquals(user1.email, partnership.partnerEmail)
            assertEquals(user1.id, partnership.partnerId)
            assertFalse(partnership.initiatedByMe)
        }
    }

    @Test
    fun `POST accept should return 404 for non-existent invitation`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val response = user.acceptInviteRaw("00000000-0000-0000-0000-000000000000")

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `POST accept should return 403 when user is not the recipient`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            val user3 = registerUser()
            val invitation = user1.sendInvite(user2.email)

            // When - user3 tries to accept invitation meant for user2
            val response = user3.acceptInviteRaw(invitation.id)

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `POST reject should reject invitation`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            val invitation = user1.sendInvite(user2.email)

            // When
            user2.rejectInvite(invitation.id)

            // Then - invitation should no longer appear
            val invitations = user2.getInvitations()
            assertFalse(invitations.received.any { it.id == invitation.id })
            assertFalse(invitations.sent.any { it.id == invitation.id })
        }
    }

    @Test
    fun `POST reject should return 404 for non-existent invitation`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val response = user.rejectInviteRaw("00000000-0000-0000-0000-000000000000")

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `POST reject should return 403 when user is not the recipient`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            val user3 = registerUser()
            val invitation = user1.sendInvite(user2.email)

            // When - user3 tries to reject invitation meant for user2
            val response = user3.rejectInviteRaw(invitation.id)

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `GET current should return active partnership`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()

            // When
            val partnership = user1.getCurrentPartnership()

            // Then
            assertNotNull(partnership)
            assertEquals("active", partnership.status)
            assertEquals(user2.email, partnership.partnerEmail)
        }
    }

    @Test
    fun `GET current should return 404 when no active partnership`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val partnership = user.getCurrentPartnership()

            // Then
            assertNull(partnership)
        }
    }

    @Test
    fun `GET current should return 404 when only pending invitation exists`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            user1.sendInvite(user2.email)

            // When - user1 has sent invitation but it's not accepted
            val partnership = user1.getCurrentPartnership()

            // Then
            assertNull(partnership)
        }
    }

    @Test
    fun `DELETE current should end active partnership`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()

            // Verify partnership exists
            assertNotNull(user1.getCurrentPartnership())

            // When
            user1.endPartnership()

            // Then - both users should have no active partnership
            assertNull(user1.getCurrentPartnership())
            assertNull(user2.getCurrentPartnership())
        }
    }

    @Test
    fun `DELETE current should return 404 when no active partnership`() = runBlocking {
        utils.run {
            // Given
            val user = registerUser()

            // When
            val response = user.endPartnershipRaw()

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `partners should be able to create conflicts together`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()

            // When - user1 creates conflict
            val conflict = user1.createConflict()

            // Then - user2 should see the conflict
            val user2Conflicts = user2.getConflicts()
            assertTrue(user2Conflicts.any { it.id == conflict.id })
        }
    }

    @Test
    fun `non-partners should not see each other's conflicts`() = runBlocking {
        utils.run {
            // Given
            val user1 = registerUser()
            val user2 = registerUser()
            val user3 = registerUser()

            // User1 and User2 are partners
            val invitation = user1.sendInvite(user2.email)
            user2.acceptInvite(invitation.id)

            // User1 creates conflict
            user1.createConflict()

            // When - User3 (not a partner) tries to get conflicts
            val user3Conflicts = user3.getConflicts()

            // Then - User3 should not see User1's conflicts
            assertTrue(user3Conflicts.isEmpty())
        }
    }

    @Test
    fun `after ending partnership users should not see shared data`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()
            val user3 = registerUser()
            user1.createConflict()

            // When - end partnership
            user1.endPartnership()

            // Then - user2 should still see old conflicts but can't create new ones with user1
            val user2Conflicts = user2.getConflicts()
            assertTrue(user2Conflicts.isNotEmpty(), "User2 should still see historical conflicts")

            user3.acceptInvite(user2.sendInvite(user3.email).id)

            val user2NewConflict = user2.createConflict()
            val user1Conflicts = user1.getConflicts()
            assertFalse(user1Conflicts.any { it.id == user2NewConflict.id },
                "User1 should not see User2's new conflicts after partnership ended")
        }
    }

    @Test
    fun `user can create new partnership after ending previous one`() = runBlocking {
        utils.run {
            // Given - User1 partners with User2, then ends it
            val user1 = registerUser()
            val user2 = registerUser()
            val invitation1 = user1.sendInvite(user2.email)
            user2.acceptInvite(invitation1.id)
            user1.endPartnership()

            // When - User1 invites User3
            val user3 = registerUser()
            val invitation2 = user1.sendInvite(user3.email)
            user3.acceptInvite(invitation2.id)

            // Then - User1 should have active partnership with User3
            val partnership = user1.getCurrentPartnership()
            assertNotNull(partnership)
            assertEquals(user3.email, partnership.partnerEmail)
            assertEquals("active", partnership.status)
        }
    }
}
