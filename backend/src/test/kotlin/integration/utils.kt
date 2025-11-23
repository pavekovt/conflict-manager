package me.pavekovt.integration

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.PartnerInviteRequest
import me.pavekovt.dto.PartnershipDTO
import me.pavekovt.dto.UserDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.RegisterRequest
import me.pavekovt.dto.exchange.SubmitResolutionRequest
import java.util.UUID
import kotlin.test.assertEquals

data class Partners(
    val user1Email: String,
    val user2Email: String,
)

data class TestUser(
    val email: String,
    val token: String
)

open class TestSdkUtils(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    suspend fun registerPartners(partners: Partners): Pair<TestUser, TestUser> {
        val user1 = registerUser(partners.user1Email + UUID.randomUUID().toString(), name = "user1")
        val user2 = registerUser(partners.user2Email  + UUID.randomUUID().toString(), name = "user2")

        val partnershipId = user1.sendInvite(user2.email).id
        user2.acceptInvite(partnershipId)

        return Pair(user1, user2)
    }

    suspend fun registerUser(email: String, name: String): TestUser {
        val registerRequest = RegisterRequest(email = email, name = name, password = "password123")
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val data = response.body<AuthResponse>()
        if (data.user == null) {
            throw IllegalStateException("User is null!")
        }
        return TestUser(data.user.email, data.token)
    }

    suspend fun TestUser.sendInvite(email: String, expectedStatus: HttpStatusCode = HttpStatusCode.Created): PartnershipDTO {
        val request = PartnerInviteRequest(email)
        val response = client.post("$baseUrl/api/partnerships/invite") {
            contentType(ContentType.Application.Json)
            setBody(request)
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(expectedStatus, response.status)

        return response.body<PartnershipDTO>()
    }

    suspend fun TestUser.createConflict(): ConflictDTO {
        val response = client.post("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        return response.body<ConflictDTO>()
    }

    suspend fun TestUser.getConflicts(): List<ConflictDTO> {
        val response = client.get("$baseUrl/api/conflicts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<List<ConflictDTO>>()
    }

    suspend fun TestUser.getConflict(id: String): ConflictDTO {
        val response = client.get("$baseUrl/api/conflicts/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<ConflictDTO>()
    }

    suspend fun TestUser.submitConflictResolution(id: String): ConflictDTO {
        val response = submitRawConflictResolution(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<ConflictDTO>()
    }

    suspend fun TestUser.submitRawConflictResolution(id: String): HttpResponse {
        val response = client.post("$baseUrl/api/conflicts/${id}/resolutions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(SubmitResolutionRequest(resolutionText = "My understanding of the resolution"))
        }

        return response
    }

    suspend fun TestUser.approveSummary(id: String): Boolean {
        val response = client.post("$baseUrl/api/conflicts/$id/approve") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }

    suspend fun TestUser.requestConflictRefinement(id: String): Boolean {
        val response = client.post("$baseUrl/api/conflicts/${id}/request-refinement") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }

    suspend fun TestUser.archiveConflict(id: String): Boolean {
        val response = client.post("$baseUrl/api/conflicts/${id}/archive") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }

    suspend fun TestUser.getConflictSummary(id: String): AISummaryDTO {
        val response = getRawConflictSummary(id)
        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<AISummaryDTO>()
    }

    suspend fun TestUser.getRawConflictSummary(id: String): HttpResponse {
        return client.get("$baseUrl/api/conflicts/${id}/summary") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.acceptInvite(id: String): PartnershipDTO {
        val request = PartnerInviteRequest(email)
        val response = client.post("$baseUrl/api/partnerships/$id/accept") {
            contentType(ContentType.Application.Json)
            setBody(request)
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<PartnershipDTO>()
    }
}

