package me.pavekovt.integration

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
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
import kotlinx.datetime.LocalDateTime
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.PartnerInviteRequest
import me.pavekovt.dto.PartnershipDTO
import me.pavekovt.dto.PartnershipInvitationsDTO
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.dto.UserDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.CreateNoteRequest
import me.pavekovt.dto.exchange.CreateRetrospectiveRequest
import me.pavekovt.dto.exchange.RegisterRequest
import me.pavekovt.dto.exchange.SubmitResolutionRequest
import me.pavekovt.dto.exchange.UpdateNoteRequest
import me.pavekovt.entity.NoteStatus
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Instant

data class Partners(
    val user1Email: String,
    val user2Email: String,
)

data class TestUser(
    val email: String,
    val name: String,
    val token: String,
    val id: String,
)

open class TestSdkUtils(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    suspend fun registerPartners(
        partners: Partners = Partners("${UUID.randomUUID()}@test.com", "${UUID.randomUUID()}@test.com"))
    : Pair<TestUser, TestUser> {
        val user1 = registerUser(partners.user1Email, name = "user1")
        val user2 = registerUser(partners.user2Email, name = "user2")

        val partnershipId = user1.sendInvite(user2.email).id
        user2.acceptInvite(partnershipId)

        return Pair(user1, user2)
    }

    suspend fun registerUser(email: String = "${UUID.randomUUID()}@test.com", name: String = "Some name ${UUID.randomUUID().toString().take(3)}"): TestUser {
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
        return TestUser(data.user.email, data.user.name, data.token, data.user.id)
    }

    suspend fun TestUser.sendInvite(email: String, expectedStatus: HttpStatusCode = HttpStatusCode.Created): PartnershipDTO {
        val response = sendInviteRaw(email)
        assertEquals(expectedStatus, response.status)
        return response.body<PartnershipDTO>()
    }

    suspend fun TestUser.sendInviteRaw(email: String): HttpResponse {
        val request = PartnerInviteRequest(email)
        return client.post("$baseUrl/api/partnerships/invite") {
            contentType(ContentType.Application.Json)
            setBody(request)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
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

    suspend fun TestUser.getDecisionRaw(id: String): HttpResponse {
        return client.get("$baseUrl/api/decisions/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getDecision(id: String): DecisionDTO {
        val response = getDecisionRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.getDecisionsRaw(): HttpResponse {
        return client.get("$baseUrl/api/decisions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getDecisions(): List<DecisionDTO> {
        val response = getDecisionsRaw()

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.reviewDecisionRaw(id: String): HttpResponse {
        return client.patch("$baseUrl/api/decisions/$id/review") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.reviewDecision(id: String): DecisionDTO {
        val response = reviewDecisionRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.archiveDecisionRaw(id: String): HttpResponse {
        return client.patch("$baseUrl/api/decisions/$id/archive") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.archiveDecision(id: String): DecisionDTO {
        val response = archiveDecisionRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.createNoteRaw(text: String = "Note text"): HttpResponse {
        return client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = text))
        }
    }

    suspend fun TestUser.createNote(text: String = "Note text"): NoteDTO {
        val response = createNoteRaw(text)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.deleteNoteRaw(id: String): HttpResponse {
        return client.delete("$baseUrl/api/notes/${id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.deleteNote(id: String): Boolean {
        val response = deleteNoteRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }


    suspend fun TestUser.getNoteRaw(id: String): HttpResponse {
        return client.get("$baseUrl/api/notes/${id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getNote(id: String): NoteDTO {
        val response = getNoteRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.patchNoteStatusRaw(id: String, noteStatus: NoteStatus): HttpResponse {
        return client.patch("$baseUrl/api/notes/${id}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(UpdateNoteRequest(status = noteStatus.name))
        }
    }

    suspend fun TestUser.patchNoteStatus(id: String, noteStatus: NoteStatus): NoteDTO {
        val response = patchNoteStatusRaw(id, noteStatus)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.createRetrospectiveRaw(scheduledDate: String?, users: List<String>?): HttpResponse {
        return client.post("$baseUrl/api/retrospectives") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest(scheduledDate = scheduledDate))
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun TestUser.createRetrospective(scheduledDate: String? = null, users: List<String>? = null): RetrospectiveDTO {
        val response = createRetrospectiveRaw(scheduledDate, users)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.getRetrospectiveRaw(id: String): HttpResponse {
        return client.get("$baseUrl/api/retrospectives/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getRetrospective(id: String): RetrospectiveDTO {
        val response = getRetrospectiveRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.getRetrospectivesRaw(): HttpResponse {
        return client.get("$baseUrl/api/retrospectives") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getRetrospectives(): List<RetrospectiveDTO> {
        val response = getRetrospectivesRaw()

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.addRetrospectiveNoteRaw(id: String, noteId: String): HttpResponse {
        return client.post("$baseUrl/api/retrospectives/${id}/add-note") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(AddNoteToRetroRequest(noteId = noteId))
        }
    }

    suspend fun TestUser.addRetrospectiveNote(id: String, noteId: String): Boolean {
        val response = addRetrospectiveNoteRaw(id, noteId)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }

    suspend fun TestUser.getRetrospectiveNotesRaw(id: String): HttpResponse {
        return client.get("$baseUrl/api/retrospectives/${id}/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getRetrospectiveNotes(id: String): RetrospectiveWithNotesDTO {
        val response = getRetrospectiveNotesRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body()
    }

    suspend fun TestUser.completeRetroRaw(id: String, topics: String): HttpResponse {
        return client.post("$baseUrl/api/retrospectives/$id/complete") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CompleteRetroRequest(finalSummary = topics))
        }
    }

    suspend fun TestUser.completeRetro(id: String, topics: String = "We discussed important topics"): Boolean {
        val response = completeRetroRaw(id, topics)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }

    suspend fun TestUser.cancelRetroRaw(id: String): HttpResponse {
        return client.patch("$baseUrl/api/retrospectives/$id/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.cancelRetro(id: String): Boolean {
        val response = cancelRetroRaw(id)

        assertEquals(HttpStatusCode.OK, response.status)

        return response.body<Map<String, Boolean>>()["success"]!!
    }

    suspend fun TestUser.acceptInvite(id: String): PartnershipDTO {
        val response = acceptInviteRaw(id)
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<PartnershipDTO>()
    }

    suspend fun TestUser.acceptInviteRaw(id: String): HttpResponse {
        return client.post("$baseUrl/api/partnerships/$id/accept") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.rejectInvite(id: String) {
        val response = rejectInviteRaw(id)
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    suspend fun TestUser.rejectInviteRaw(id: String): HttpResponse {
        return client.post("$baseUrl/api/partnerships/$id/reject") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getInvitations(): PartnershipInvitationsDTO {
        val response = getInvitationsRaw()
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }

    suspend fun TestUser.getInvitationsRaw(): HttpResponse {
        return client.get("$baseUrl/api/partnerships/invitations") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.getCurrentPartnership(): PartnershipDTO? {
        val response = getCurrentPartnershipRaw()
        return when (response.status) {
            HttpStatusCode.OK -> response.body<PartnershipDTO>()
            HttpStatusCode.NotFound -> null
            else -> throw IllegalStateException("Unexpected status: ${response.status}")
        }
    }

    suspend fun TestUser.getCurrentPartnershipRaw(): HttpResponse {
        return client.get("$baseUrl/api/partnerships/current") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    suspend fun TestUser.endPartnership() {
        val response = endPartnershipRaw()
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    suspend fun TestUser.endPartnershipRaw(): HttpResponse {
        return client.delete("$baseUrl/api/partnerships/current") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

