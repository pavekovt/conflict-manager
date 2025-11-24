package me.pavekovt.integration

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.RegisterRequest
import me.pavekovt.dto.exchange.SubmitResolutionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecisionsApiTest : IntegrationTestBase() {

    private suspend fun registerUser(email: String, name: String): String {
        val registerRequest = RegisterRequest(email = email, name = name, password = "password123")
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        val authResponse = response.body<AuthResponse>()
        return authResponse.token
    }

    private suspend fun createConflictWithApprovedSummary(user1Token: String, user2Token: String): String {
        // Create conflict
        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val conflict = createResponse.body<ConflictDTO>()

        // Both users submit resolutions
        assertEquals(HttpStatusCode.OK, client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            setBody(SubmitResolutionRequest(resolutionText = "Resolution from user 1"))
        }.status, "User 1 resolution failed")

        assertEquals(HttpStatusCode.OK, client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user2Token")
            setBody(SubmitResolutionRequest(resolutionText = "Resolution from user 2"))
        }.status, "User 2 resolution failed")

        // Both users approve summary
        assertEquals(HttpStatusCode.OK, client.post("$baseUrl/api/conflicts/${conflict.id}/approve") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }.status, "User 1 approve failed")

        assertEquals(HttpStatusCode.OK, client.post("$baseUrl/api/conflicts/${conflict.id}/approve") {
            header(HttpHeaders.Authorization, "Bearer $user2Token")
        }.status, "User 2 approve failed")

        return conflict.id
    }

    @Test
    fun `GET decisions should return decision backlog`() = runBlocking {
        utils.run {
            // Given - create an approved conflict (which creates a decision)
            val (user1, user2) = registerPartners()
            createConflictWithApprovedSummary(user1.token, user2.token)

            // When
            val decisions = user1.getDecisions()

            // Then
            assertTrue(decisions.isNotEmpty())

            val decision = decisions.first()
            assertNotNull(decision.id)
            assertNotNull(decision.summary)
            assertEquals("active", decision.status)
            assertEquals(null, decision.reviewedAt)
        }
    }

    @Test
    fun `GET decisions with status filter should return only matching decisions`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()
            createConflictWithApprovedSummary(user1.token, user2.token)

            // Get first decision
            val decisionId = user1.getDecisions().first().id

            // Mark it as reviewed
            user1.reviewDecision(decisionId)

            // When - filter by reviewed status
            val response = client.get("$baseUrl/api/decisions?status=reviewed") {
                header(HttpHeaders.Authorization, "Bearer ${user1.token}")
            }

            // Then
            assertEquals(HttpStatusCode.OK, response.status)

            val decisions = response.body<List<DecisionDTO>>()
            assertTrue(decisions.all { it.status == "reviewed" })
        }
    }

    @Test
    fun `GET decision by id should return the decision`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()
            createConflictWithApprovedSummary(user1.token, user2.token)

            val decisionId = user1.getDecisions().first().id

            // When
            val decision = user1.getDecision(decisionId)

            // Then
            assertEquals(decisionId, decision.id)
        }
    }

    @Test
    fun `GET decision by id should return 404 for non-existent decision`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()

            // When
            val response = user.getDecisionRaw("00000000-0000-0000-0000-000000000000")

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `PATCH review should mark decision as reviewed`(): Unit = runBlocking {
        utils.run {

            // Given
            val (user1, user2) = registerPartners()
            createConflictWithApprovedSummary(user1.token, user2.token)

            val decisions = user1.getDecisions()
            val decisionId = decisions.first().id

            // When
            val reviewedResponse = user1.reviewDecision(decisionId)

            // Then
            assertEquals("reviewed", reviewedResponse.status)
            assertNotNull(reviewedResponse.reviewedAt)
        }
    }

    @Test
    fun `PATCH archive should archive decision`() = runBlocking {
        utils.run {

            // Given
            val (user1, user2) = registerPartners()
            createConflictWithApprovedSummary(user1.token, user2.token)
            val decisionId = user1.getDecisions().first().id

            // When
            val archiveResponse = user1.archiveDecision(decisionId)

            // Then
            assertEquals("archived", archiveResponse.status)
        }
    }

    @Test
    fun `decisions should be visible to both users in the conflict`() = runBlocking {
        utils.run {

            // Given
            val (user1, user2) = registerPartners()
            createConflictWithApprovedSummary(user1.token, user2.token)

            // When - both users request decisions
            val user1Decisions = user1.getDecisions()

            val user2Decisions = user2.getDecisions()

            // Then - both should see the same decision
            assertTrue(user1Decisions.isNotEmpty())
            assertTrue(user2Decisions.isNotEmpty())

            // Should contain the same decision ID
            val commonDecisionId = user1Decisions.first().id
            assertTrue(user2Decisions.any { it.id == commonDecisionId })
        }
    }
}
