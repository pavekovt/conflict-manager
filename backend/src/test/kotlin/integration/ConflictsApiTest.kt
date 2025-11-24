package me.pavekovt.integration

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import me.pavekovt.entity.ConflictStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConflictsApiTest : IntegrationTestBase() {

    @Test
    fun `POST conflicts should create a new conflict`() = runBlocking {
        utils.run {
            // Given
            val (user) = utils.registerPartners()

            // When
            val conflict = user.createConflict()

            // Then
            assertNotNull(conflict.id)
            assertNotNull(conflict.initiatedBy)
            assertEquals(ConflictStatus.PENDING_RESOLUTIONS, conflict.status)
            assertEquals(false, conflict.myResolutionSubmitted)
            assertEquals(false, conflict.partnerResolutionSubmitted)
            assertEquals(false, conflict.summaryAvailable)
        }
    }

    @Test
    fun `GET conflicts should return user's conflicts`() = runBlocking {
        // Given
        utils.run {
            val (user1) = utils.registerPartners()

            // Given
            user1.createConflict()

            // When
            val response = user1.getConflicts()

            // Then
            assertTrue(response.isNotEmpty())
        }
    }

    @Test
    fun `GET conflict by id should return conflict details`() = runBlocking {
        utils.run {
            // Given
            val (user1) = utils.registerPartners()
            val conflict = user1.createConflict()

            // When
            val response = user1.getConflict(conflict.id)

            // Then
            assertEquals(response.id, conflict.id)
        }
    }

    @Test
    fun `GET conflict by id should return partners conflict details`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflict()

            // When
            val response = user2.getConflict(conflict.id)

            // Then
            assertEquals(response.id, conflict.id)
        }
    }

    @Test
    fun `GET conflict by id should return partners conflicts`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflict()

            // When
            val response = user2.getConflicts()

            // Then
            assertTrue(response.isNotEmpty())
            assertEquals(conflict.id, response.first().id)
        }
    }

    @Test
    fun `POST resolution should submit user's resolution`() = runBlocking {
        utils.run {
            // Given
            val (user1) = utils.registerPartners()
            val conflict = user1.createConflict()

            // When
            val response = user1.submitConflictResolution(conflict.id)

            // Then
            assertEquals(true, response.myResolutionSubmitted)
        }
    }

    @Test
    fun `POST resolution should fail when resolution already submitted`(): Unit = runBlocking {
        utils.run {
            // Given
            val (user1) = utils.registerPartners()
            val conflict = user1.createConflict()

            // Submit first resolution
            user1.submitConflictResolution(conflict.id)

            // When
            val response = user1.submitRawConflictResolution(conflict.id)

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `AI summary should be generated when both partners submit resolutions`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflict()

            // User 1 submits resolution
            user1.submitConflictResolution(conflict.id)

            // When - User 2 submits resolution
            user2.submitConflictResolution(conflict.id)

            // Then - summary should be available
            val summary = user1.getConflictSummary(conflict.id)

            assertNotNull(summary.summaryText)
            assertTrue(summary.summaryText.contains("We decided", ignoreCase = true))
        }
    }

    @Test
    fun `GET summary should fail before both resolutions submitted`(): Unit = runBlocking {
        utils.run {
            // Given
            val (user1) = utils.registerPartners()
            val conflict = user1.createConflict()

            // When
            val response = user1.getRawConflictSummary(conflict.id)

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    //TODO: fix the test
    @Test
    fun `PATCH approve should approve summary`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflict()

            user1.submitConflictResolution(conflict.id)
            user2.submitConflictResolution(conflict.id)

            // When - User 1 approves summary
            user1.approveSummary(conflict.id)

            // Verify summary shows user1 approved
            val response = user1.getConflictSummary(conflict.id)
            assertEquals(true, response.approvedByMe)
        }
    }

    @Test
    fun `PATCH request-refinement should change status to refinement`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflict()

            user1.submitConflictResolution(conflict.id)
            user2.submitConflictResolution(conflict.id)

            // When
            user1.requestConflictRefinement(conflict.id)

            // Then
            val updatedConflict = user1.getConflict(conflict.id)
            assertEquals(ConflictStatus.REFINEMENT, updatedConflict.status)
        }
    }

    @Test
    fun `PATCH archive should archive conflict`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflict()

            // When
            user1.archiveConflict(conflict.id)

            // Then
            val updatedConflict = user1.getConflict(conflict.id)
            assertEquals(ConflictStatus.ARCHIVED, updatedConflict.status)
        }
    }
}
