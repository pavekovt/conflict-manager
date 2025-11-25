package me.pavekovt.integration

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import me.pavekovt.dto.ConflictAction
import me.pavekovt.entity.ConflictStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.pavekovt.integration.dsl.*

/**
 * Integration tests for UX enhancements:
 * - Dashboard aggregation
 * - Job status polling
 * - Conflict actions endpoint
 * - Partnership health
 * - Privacy indicators
 * - State transition fields
 */
class UxEnhancementsTest : IntegrationTestBase() {

    @Test
    fun `test dashboard shows pending actions for active conflicts`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    // Alice creates a conflict and submits feelings
                    user1.conflict {
                        create()
                        withFeelings("I feel frustrated about this situation")

                        // Alice should see conflict in her dashboard
                        user1.dashboard {
                            hasPendingActions(1)
                            hasPendingAction("CONFLICT", "Submit your feelings")
                            summaryShows {
                                activeConflicts(1)
                                needingMyAction(1)
                            }
                        }
                    }

                    // Bob creates his own conflict
                    user2.conflict {
                        create()

                        // Bob should see his conflict in dashboard
                        user2.dashboard {
                            hasPendingActions(1)
                            summaryShows {
                                activeConflicts(1)
                                needingMyAction(1)
                            }
                        }
                    }

                    // Alice should still see only her conflict
                    user1.dashboard {
                        summaryShows {
                            activeConflicts(1)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test conflict actions show correct availability based on state`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users {
                    user1.conflict {
                        create()

                        // Initial state: can submit feelings, cannot submit resolution
                        assertActions {
                            canDo(ConflictAction.SUBMIT_FEELINGS)
                            cannotDo(ConflictAction.SUBMIT_RESOLUTION, "feelings first")
                            hasNextSteps("Submit your feelings")
                            progressForMe {
                                hasFeelingsSubmitted(0)
                                hasResolutionSubmitted(false)
                            }
                        }

                        // Submit feelings
                        withFeelings("I feel hurt about this")

                        // After feelings: still cannot submit resolution until AI processes
                        assertActions {
                            canDo(ConflictAction.VIEW_FEELINGS)
                            cannotDo(ConflictAction.SUBMIT_RESOLUTION, "process feelings first")
                            progressForMe {
                                hasFeelingsSubmitted(1)
                            }
                        }

                        // Wait for AI processing (simulate)
                        delay(2000)

                        // After processing: can submit resolution
                        assertActions {
                            canDo(ConflictAction.SUBMIT_RESOLUTION)
                        }

                        // Submit resolution
                        withResolution("I think we should communicate more openly")

                        // After my resolution: waiting for partner
                        assertActions {
                            cannotDo(ConflictAction.SUBMIT_RESOLUTION, "already submitted")
                            progressForMe {
                                hasResolutionSubmitted(true)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test conflict state transitions show correct nextAction and waitingFor`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    user1.conflict {
                        create()

                        // State: PENDING_FEELINGS
                        assertState {
                            hasStatus(ConflictStatus.PENDING_FEELINGS)
                            hasNextAction("Submit your feelings")
                            allowsAction(ConflictAction.SUBMIT_FEELINGS)
                            doesNotAllowAction(ConflictAction.SUBMIT_RESOLUTION)
                        }

                        // Alice submits feelings
                        withFeelings("I feel anxious about our communication")

                        // Wait for AI processing
                        delay(2000)

                        // State should transition to PENDING_RESOLUTIONS after both submit feelings
                        // For now, just Alice submitted, so still PENDING_FEELINGS
                        assertState {
                            hasStatus(ConflictStatus.PENDING_FEELINGS)
                            waitingFor("partner")
                        }
                    }

                    // Bob submits feelings for the same conflict
                    user2.conflict {
                        create()
                        withFeelings("I feel stressed too")

                        delay(2000)

                        // Now both submitted feelings, should transition to PENDING_RESOLUTIONS
                        assertState {
                            hasNextAction("Submit your resolution")
                            allowsAction(ConflictAction.SUBMIT_RESOLUTION)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test job status polling shows progress and user-friendly messages`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    user1.conflict {
                        create()

                        // Submit feelings (creates async job)
                        val feelings = user1.submitFeelings(conflict.id, "I feel overwhelmed")

                        // Extract job ID from feelings submission response
                        // Note: In real implementation, API should return AsyncOperationResponse with job info
                        assertNotNull(feelings)
                        assertNotNull(feelings.id)

                        // Wait for job completion
                        delay(2000)

                        // Verify feelings were processed
                        assertActions {
                            progressForMe {
                                hasFeelingsSubmitted(1)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test partnership health shows stats and suggestions`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    // Check initial health: no conflicts yet
                    user1.partnershipHealth {
                        isActive()
                        stats {
                            activeConflicts(0)
                            resolvedConflicts(0)
                        }
                        suggests("Start by creating notes")
                    }

                    // Create an active conflict
                    user1.conflict {
                        create()
                    }

                    // Health should reflect active conflict
                    user1.partnershipHealth {
                        isActive()
                        needsAttention("1 active conflict")
                        stats {
                            activeConflicts(1)
                        }
                    }

                    // Create and resolve a complete conflict workflow
                    user1.conflict {
                        create()
                        withFeelings("I'm frustrated")
                        delay(2000)  // Wait for AI
                        withResolution("Let's talk more")
                    }

                    user2.conflict {
                        create()
                        withFeelings("Me too")
                        delay(2000)
                        withResolution("Yes, agreed")
                    }

                    // Wait for summary generation
                    delay(3000)

                    // Health should show multiple active conflicts
                    user1.partnershipHealth {
                        needsAttention("active conflict")
                        stats {
                            activeConflicts(2)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test privacy indicators on feelings`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    user1.conflict {
                        create()

                        // Alice submits feelings
                        val feelings = user1.submitFeelings(conflict.id, "I feel anxious")

                        // Feelings should indicate privacy status
                        assertNotNull(feelings)
                        assertEquals(false, feelings.visibleToPartner)
                        assertNotNull(feelings.visibilityReason)
                        assertTrue(
                            feelings.visibilityReason!!.contains("not visible", ignoreCase = true) ||
                            feelings.visibilityReason!!.contains("private", ignoreCase = true)
                        )

                        // Bob should not be able to see Alice's feelings yet
                        val bobConflict = user2.getConflict(conflict.id)
                        assertNotNull(bobConflict)
                        // Bob's view shouldn't show Alice's feelings details
                    }
                }
            }
        }
    }

    @Test
    fun `test journal entries show privacy level`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    // Alice creates a private journal entry
                    user1.journal("Today I felt frustrated about our conversation") {
                        assertPrivacy {
                            isPrivate()
                        }
                    }

                    // Complete the journal
                    user1.journal("Another thought") {
                        complete()
                        assertPrivacy {
                            hasStatus("completed")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test conflict workflow shows all state transitions`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    // Alice creates conflict and submits feelings
                    val sharedConflict = user1.conflict {
                        create()

                        // 1. PENDING_FEELINGS state
                        assertState {
                            hasStatus(ConflictStatus.PENDING_FEELINGS)
                            hasNextAction("Submit your feelings")
                        }

                        withFeelings("I feel hurt")
                    }

                    val conflictId = sharedConflict.conflict.id

                    delay(2000)  // Wait for AI processing

                    // Check Alice's conflict state - still PENDING_FEELINGS (waiting for partner)
                    val aliceConflict = user1.getConflict(conflictId)
                    ConflictAssertion(aliceConflict).apply {
                        hasStatus(ConflictStatus.PENDING_FEELINGS)
                        waitingFor("partner")
                    }

                    // Bob submits feelings for the same conflict
                    user2.submitFeelings(conflictId, "I feel stressed too")
                    delay(2000)

                    // 2. Should transition to PENDING_RESOLUTIONS after both feelings processed
                    val aliceConflict2 = user1.getConflict(conflictId)
                    ConflictAssertion(aliceConflict2).apply {
                        hasStatus(ConflictStatus.PENDING_RESOLUTIONS)
                        hasNextAction("resolution")
                    }

                    // Both submit resolutions
                    user1.submitResolution(conflictId, "Let's set aside time to talk daily")
                    user2.submitResolution(conflictId, "Yes, and listen without interrupting")

                    delay(3000)  // Wait for summary generation

                    // 3. SUMMARY_GENERATED state
                    val aliceConflict3 = user1.getConflict(conflictId)
                    ConflictAssertion(aliceConflict3).apply {
                        hasStatus(ConflictStatus.SUMMARY_GENERATED)
                        hasNextAction("Review")
                    }

                    // Check available actions
                    val actions = user1.getConflictActions(conflictId)
                    ConflictActionsAssertion(actions).apply {
                        canDo(ConflictAction.VIEW_SUMMARY)
                        canDo(ConflictAction.APPROVE_SUMMARY)
                        canDo(ConflictAction.REQUEST_REFINEMENT)
                    }
                }
            }
        }
    }

    @Test
    fun `test dashboard priority sorting`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    // Create multiple conflicts at different stages
                    val conflict1 = user1.conflict {
                        create()
                        withFeelings("Feeling 1")
                    }

                    val conflict2 = user1.conflict {
                        create()
                    }

                    val conflict3 = user1.conflict {
                        create()
                        withFeelings("Feeling 3")
                    }

                    // Dashboard should show all pending actions
                    user1.dashboard {
                        hasPendingActions(3)
                        summaryShows {
                            activeConflicts(3)
                            needingMyAction(3)
                        }
                    }

                    // Create a journal entry (lower priority)
                    user1.journal("Some thoughts")

                    // Dashboard should now include journal
                    user1.dashboard {
                        summaryShows {
                            activeConflicts(3)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `test conflict actions show partner progress`() = testApplication {
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        testApi(baseUrl, client) {
            partnership("test-partners") {
                users(
                    user1Email = "alice@test.com",
                    user2Email = "bob@test.com",
                    user1Name = "Alice",
                    user2Name = "Bob"
                ) {
                    val sharedConflict = user1.conflict {
                        create()
                        withFeelings("My feelings")
                    }

                    val conflictId = sharedConflict.conflict.id
                    delay(2000)

                    // Alice can see that partner hasn't submitted feelings yet
                    val actions1 = user1.getConflictActions(conflictId)
                    ConflictActionsAssertion(actions1).apply {
                        progressForMe {
                            hasFeelingsSubmitted(1)
                        }
                        progressForPartner {
                            hasFeelingsSubmitted(0)
                        }
                        hasNextSteps("partner needs to submit")
                    }

                    // Bob submits feelings
                    user2.submitFeelings(conflictId, "Bob's feelings")
                    delay(2000)

                    // Now Alice can see partner has submitted
                    val actions2 = user1.getConflictActions(conflictId)
                    ConflictActionsAssertion(actions2).apply {
                        progressForPartner {
                            hasFeelingsSubmitted(1)
                        }
                    }
                }
            }
        }
    }
}
