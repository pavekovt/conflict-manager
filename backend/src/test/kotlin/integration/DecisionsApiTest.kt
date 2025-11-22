package me.pavekovt.integration

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.RegisterRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class DecisionResponse(
    val id: String,
    val conflictId: String?,
    val summary: String,
    val category: String?,
    val status: String,
    val createdAt: String,
    val reviewedAt: String?
)

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
        val conflict = createResponse.body<ConflictResponse>()

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
        // Given - create an approved conflict (which creates a decision)
        val user1Token = registerUser("decision1@example.com", "User 1")
        val user2Token = registerUser("decision2@example.com", "User 2")

        createConflictWithApprovedSummary(user1Token, user2Token)

        // When
        val response = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val decisions = response.body<List<DecisionResponse>>()
        assertTrue(decisions.isNotEmpty())

        val decision = decisions.first()
        assertNotNull(decision.id)
        assertNotNull(decision.summary)
        assertEquals("active", decision.status)
        assertEquals(null, decision.reviewedAt)
    }

    @Test
    fun `GET decisions with status filter should return only matching decisions`() = runBlocking {
        // Given
        val user1Token = registerUser("decfilter1@example.com", "User A")
        val user2Token = registerUser("decfilter2@example.com", "User B")

        createConflictWithApprovedSummary(user1Token, user2Token)

        // Get first decision
        val allDecisionsResponse = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val allDecisions = allDecisionsResponse.body<List<DecisionResponse>>()
        val firstDecision = allDecisions.first()

        // Mark it as reviewed
        client.patch("$baseUrl/api/decisions/${firstDecision.id}/review") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // When - filter by reviewed status
        val response = client.get("$baseUrl/api/decisions?status=reviewed") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val decisions = response.body<List<DecisionResponse>>()
        assertTrue(decisions.all { it.status == "reviewed" })
    }

    @Test
    fun `GET decision by id should return the decision`() = runBlocking {
        // Given
        val user1Token = registerUser("getdec1@example.com", "User X")
        val user2Token = registerUser("getdec2@example.com", "User Y")

        createConflictWithApprovedSummary(user1Token, user2Token)

        val allResponse = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val allDecisions = allResponse.body<List<DecisionResponse>>()
        val decisionId = allDecisions.first().id

        // When
        val response = client.get("$baseUrl/api/decisions/$decisionId") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val decision = response.body<DecisionResponse>()
        assertEquals(decisionId, decision.id)
    }

    @Test
    fun `GET decision by id should return 404 for non-existent decision`() = runBlocking {
        // Given
        val token = registerUser("notfound@example.com", "User")

        // When
        val response = client.get("$baseUrl/api/decisions/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH review should mark decision as reviewed`(): Unit = runBlocking {
        // Given
        val user1Token = registerUser("review1@example.com", "User M")
        val user2Token = registerUser("review2@example.com", "User N")

        createConflictWithApprovedSummary(user1Token, user2Token)

        val allResponse = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val allDecisions = allResponse.body<List<DecisionResponse>>()
        val decisionId = allDecisions.first().id

        // When
        val reviewResponse = client.patch("$baseUrl/api/decisions/$decisionId/review") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, reviewResponse.status)

        val reviewedDecision = reviewResponse.body<DecisionResponse>()
        assertEquals("reviewed", reviewedDecision.status)
        assertNotNull(reviewedDecision.reviewedAt)
    }

    @Test
    fun `PATCH archive should archive decision`() = runBlocking {
        // Given
        val user1Token = registerUser("archive1@example.com", "User P")
        val user2Token = registerUser("archive2@example.com", "User Q")

        createConflictWithApprovedSummary(user1Token, user2Token)

        val allResponse = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val allDecisions = allResponse.body<List<DecisionResponse>>()
        val decisionId = allDecisions.first().id

        // When
        val archiveResponse = client.patch("$baseUrl/api/decisions/$decisionId/archive") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, archiveResponse.status)

        val archivedDecision = archiveResponse.body<DecisionResponse>()
        assertEquals("archived", archivedDecision.status)
    }

    @Test
    fun `decisions should be visible to both users in the conflict`() = runBlocking {
        // Given
        val user1Token = registerUser("shared1@example.com", "User S")
        val user2Token = registerUser("shared2@example.com", "User T")

        createConflictWithApprovedSummary(user1Token, user2Token)

        // When - both users request decisions
        val user1Response = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        val user2Response = client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $user2Token")
        }

        // Then - both should see the same decision
        assertEquals(HttpStatusCode.OK, user1Response.status)
        assertEquals(HttpStatusCode.OK, user2Response.status)

        val user1Decisions = user1Response.body<List<DecisionResponse>>()
        val user2Decisions = user2Response.body<List<DecisionResponse>>()

        assertTrue(user1Decisions.isNotEmpty())
        assertTrue(user2Decisions.isNotEmpty())

        // Should contain the same decision ID
        val commonDecisionId = user1Decisions.first().id
        assertTrue(user2Decisions.any { it.id == commonDecisionId })
    }
}
