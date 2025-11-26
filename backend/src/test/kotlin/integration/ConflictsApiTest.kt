package me.pavekovt.integration

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.integration.dsl.testApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConflictsApiTest : IntegrationTestBase() {

    @Test
    fun `POST conflicts should create a new conflict`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                users {
                    user1.conflict {
                        create()
                        assertState {
                            hasStatus(ConflictStatus.PENDING_FEELINGS)
                            myResolutionSubmitted(false)
                            partnerResolutionSubmitted(false)
                            summaryAvailable(false)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `GET conflicts should return user's conflicts`() = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                users {
                    user1.conflict {
                        create()
                    }

                    user1.getConflicts().also {
                        assertTrue(it.isNotEmpty())
                    }
                }
            }
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
            val conflict = user1.createConflictReadyForResolutions()

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
            val conflict = user1.createConflictReadyForResolutions()

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
        testApi(baseUrl, client) {
            partnership {
                users {
                    val conflictId = user1.conflict {
                        create()
                        withFeelings()
                    }.returningId()

                    user2.conflict {
                        fetch(conflictId)
                        withFeelings()
                    }

                    user1.waitForConflictStatus(conflictId, ConflictStatus.PENDING_RESOLUTIONS)

                    user1.conflict {
                        fetch(conflictId)
                        withResolution()
                    }

                    user2.conflict {
                        fetch(conflictId)
                        withResolution()
                    }

                    user1.waitForConflictStatus(conflictId, ConflictStatus.SUMMARY_GENERATED)

                    user1.conflict {
                        fetch(conflictId)
                        assertState {
                            summaryAvailable()
                            partnerResolutionSubmitted()
                            myResolutionSubmitted()
                        }
                        summary {
                            assertState {
                                hasText("Moving forward, you'll both commit to this resolution")
                            }
                        }
                    }
                }
            }
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

    @Test
    fun `PATCH approve should approve summary`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = utils.registerPartners()
            val conflict = user1.createConflictReadyForResolutions()

            user1.submitConflictResolution(conflict.id)
            user2.submitConflictResolution(conflict.id)

            // Wait for async summary generation to complete
            user1.waitForConflictStatus(conflict.id, ConflictStatus.SUMMARY_GENERATED)

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
            val conflict = user1.createConflictReadyForResolutions()

            user1.submitConflictResolution(conflict.id)
            user2.submitConflictResolution(conflict.id)

            // Wait for async summary generation to complete
            user1.waitForConflictStatus(conflict.id, ConflictStatus.SUMMARY_GENERATED)

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
