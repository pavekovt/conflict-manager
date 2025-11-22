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
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class ConflictResponse(
    val id: String,
    val initiatedBy: String,
    val status: String,
    val createdAt: String,
    val myResolutionSubmitted: Boolean,
    val partnerResolutionSubmitted: Boolean,
    val summaryAvailable: Boolean
)

@Serializable
data class SubmitResolutionRequest(
    val resolutionText: String
)

@Serializable
data class AISummaryResponse(
    val id: String,
    val conflictId: String,
    val summaryText: String,
    val provider: String,
    val approvedByMe: Boolean,
    val approvedByPartner: Boolean,
    val createdAt: String
)

class ConflictsApiTest : IntegrationTestBase() {

    private suspend fun registerUser(email: String, name: String): String {
        val registerRequest = RegisterRequest(email = email, name = name, password = "password123")
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        val authResponse = response.body<AuthResponse>()
        return authResponse.token
    }

    @Test
    fun `POST conflicts should create a new conflict`() = runBlocking {
        // Given
        val token = registerUser("conflict1@example.com", "User 1")

        // When
        val response = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val conflict = response.body<ConflictResponse>()
        assertNotNull(conflict.id)
        assertNotNull(conflict.initiatedBy)
        assertEquals("pending_resolutions", conflict.status)
        assertEquals(false, conflict.myResolutionSubmitted)
        assertEquals(false, conflict.partnerResolutionSubmitted)
        assertEquals(false, conflict.summaryAvailable)
    }

    @Test
    fun `GET conflicts should return user's conflicts`() = runBlocking {
        // Given
        val token = registerUser("conflict2@example.com", "User 2")

        // Create a conflict
        client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // When
        val response = client.get("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val conflicts = response.body<List<ConflictResponse>>()
        assertTrue(conflicts.isNotEmpty())
    }

    @Test
    fun `GET conflict by id should return conflict details`() = runBlocking {
        // Given
        val token = registerUser("conflict3@example.com", "User 3")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val createdConflict = createResponse.body<ConflictResponse>()

        // When
        val response = client.get("$baseUrl/api/conflicts/${createdConflict.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val conflict = response.body<ConflictResponse>()
        assertEquals(createdConflict.id, conflict.id)
    }

    @Test
    fun `POST resolution should submit user's resolution`() = runBlocking {
        // Given
        val token = registerUser("conflict4@example.com", "User 4")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        // When
        val submitResponse = client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(SubmitResolutionRequest(resolutionText = "My understanding of the resolution"))
        }

        // Then
        assertEquals(HttpStatusCode.OK, submitResponse.status)

        val updatedConflict = submitResponse.body<ConflictResponse>()
        assertEquals(true, updatedConflict.myResolutionSubmitted)
    }

    @Test
    fun `POST resolution should fail when resolution already submitted`() = runBlocking {
        // Given
        val token = registerUser("conflict5@example.com", "User 5")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        // Submit first resolution
        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(SubmitResolutionRequest(resolutionText = "First resolution"))
        }

        // When - try to submit again
        val response = client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(SubmitResolutionRequest(resolutionText = "Second resolution"))
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `AI summary should be generated when both partners submit resolutions`() = runBlocking {
        // Given - User 1 creates conflict
        val user1Token = registerUser("partner1@example.com", "Partner 1")
        val user2Token = registerUser("partner2@example.com", "Partner 2")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        // User 1 submits resolution
        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            setBody(SubmitResolutionRequest(resolutionText = "User 1's understanding"))
        }

        // When - User 2 submits resolution
        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user2Token")
            setBody(SubmitResolutionRequest(resolutionText = "User 2's understanding"))
        }

        // Then - summary should be available
        val summaryResponse = client.get("$baseUrl/api/conflicts/${conflict.id}/summary") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        assertEquals(HttpStatusCode.OK, summaryResponse.status)

        val summary = summaryResponse.body<AISummaryResponse>()
        assertNotNull(summary.summaryText)
        assertTrue(summary.summaryText.contains("We decided", ignoreCase = true))
    }

    @Test
    fun `GET summary should fail before both resolutions submitted`() = runBlocking {
        // Given
        val token = registerUser("conflict6@example.com", "User 6")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        // When - try to get summary without submitting resolutions
        val response = client.get("$baseUrl/api/conflicts/${conflict.id}/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    //TODO: fix the test
    @Test
    fun `PATCH approve should approve summary`() = runBlocking {
        // Given - create conflict with both resolutions
        val user1Token = registerUser("approve1@example.com", "User A")
        val user2Token = registerUser("approve2@example.com", "User B")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            setBody(SubmitResolutionRequest(resolutionText = "Resolution 1"))
        }

        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user2Token")
            setBody(SubmitResolutionRequest(resolutionText = "Resolution 2"))
        }

        // When - User 1 approves summary
        val approveResponse = client.post("$baseUrl/api/conflicts/${conflict.id}/approve") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, approveResponse.status)

        // Verify summary shows user1 approved
        val summaryResponse = client.get("$baseUrl/api/conflicts/${conflict.id}/summary") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val summary = summaryResponse.body<AISummaryResponse>()
        assertEquals(true, summary.approvedByMe)
    }

    @Test
    fun `PATCH request-refinement should change status to refinement`() = runBlocking {
        // Given
        val user1Token = registerUser("refine1@example.com", "User X")
        val user2Token = registerUser("refine2@example.com", "User Y")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            setBody(SubmitResolutionRequest(resolutionText = "Resolution 1"))
        }

        client.post("$baseUrl/api/conflicts/${conflict.id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user2Token")
            setBody(SubmitResolutionRequest(resolutionText = "Resolution 2"))
        }

        // When
        val response = client.patch("$baseUrl/api/conflicts/${conflict.id}/request-refinement") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify status changed
        val getResponse = client.get("$baseUrl/api/conflicts/${conflict.id}") {
            header(HttpHeaders.Authorization, "Bearer $user1Token")
        }
        val updatedConflict = getResponse.body<ConflictResponse>()
        assertEquals("refinement", updatedConflict.status)
    }

    @Test
    fun `PATCH archive should archive conflict`() = runBlocking {
        // Given
        val token = registerUser("archive@example.com", "Archive User")

        val createResponse = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val conflict = createResponse.body<ConflictResponse>()

        // When
        val response = client.patch("$baseUrl/api/conflicts/${conflict.id}/archive") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify status changed
        val getResponse = client.get("$baseUrl/api/conflicts/${conflict.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val updatedConflict = getResponse.body<ConflictResponse>()
        assertEquals("archived", updatedConflict.status)
    }
}
