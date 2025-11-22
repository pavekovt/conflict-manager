package me.pavekovt.integration

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.CreateNoteRequest
import me.pavekovt.dto.exchange.CreateRetrospectiveRequest
import me.pavekovt.dto.exchange.LoginRequest
import me.pavekovt.dto.exchange.RegisterRequest
import me.pavekovt.dto.exchange.UpdateNoteRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class AddNoteToRetroRequest(
    val noteId: String
)

@Serializable
data class CompleteRetroRequest(
    val finalSummary: String
)

class RetrospectivesApiTest : IntegrationTestBase() {

    private suspend fun registerUser(email: String, name: String, password: String = "password123"): String {
        val registerRequest = RegisterRequest(email = email, name = name, password = password)
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        val authResponse = response.body<AuthResponse>()
        return authResponse.token
    }

    @Test
    fun `POST retrospectives should create a manual retrospective`() = runBlocking {
        // Given
        val token = registerUser("retro1@example.com", "Retro User 1")

        // When
        val response = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val retro = response.body<RetrospectiveDTO>()
        assertNotNull(retro.id)
        assertEquals("in_progress", retro.status)
        assertEquals(null, retro.scheduledDate)
        assertEquals(null, retro.completedAt)
    }

    @Test
    fun `POST retrospectives should create scheduled retrospective`() = runBlocking {
        // Given
        val token = registerUser("retro2@example.com", "Retro User 2")
        val scheduledDate = "2024-12-25T10:00"

        // When
        val response = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest(scheduledDate = scheduledDate))
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val retro = response.body<RetrospectiveDTO>()
        assertEquals("scheduled", retro.status)
        assertEquals(scheduledDate, retro.scheduledDate)
    }

    @Test
    fun `POST retrospectives with userIds should include specified users`() = runBlocking {
        // Given
        val user1Token = registerUser("retrouser1@example.com", "User 1")
        val user2Token = registerUser("retrouser2@example.com", "User 2", "test_pass")

        // Get user 2's ID by registering
        val user2Response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("retrouser2@example.com", "test_pass"))
        }
        val user2Auth = user2Response.body<AuthResponse>()

        assertNotNull(user2Auth.user)
        val user2Id = user2Auth.user.id

        // When - User 1 creates retro including both users
        val response = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $user1Token")
            setBody(CreateRetrospectiveRequest(userIds = listOf(user2Id)))
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val retro = response.body<RetrospectiveDTO>()
        assertNotNull(retro.id)

        // Both users should see the retrospective
        val user2RetrosResponse = client.get("$baseUrl/api/retrospectives") {
            header(HttpHeaders.Authorization, "Bearer $user2Token")
        }
        val user2Retros = user2RetrosResponse.body<List<RetrospectiveDTO>>()
        assertTrue(user2Retros.any { it.id == retro.id })
    }

    @Test
    fun `GET retrospectives should return user's retrospectives`() = runBlocking {
        // Given
        val token = registerUser("retro3@example.com", "Retro User 3")

        // Create a retrospective
        client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }

        // When
        val response = client.get("$baseUrl/api/retrospectives") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val retros = response.body<List<RetrospectiveDTO>>()
        assertTrue(retros.isNotEmpty())
    }

    @Test
    fun `GET retrospective by id should return retro details`() = runBlocking {
        // Given
        val token = registerUser("retro4@example.com", "Retro User 4")

        val createResponse = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }
        val createdRetro = createResponse.body<RetrospectiveDTO>()

        // When
        val response = client.get("$baseUrl/api/retrospectives/${createdRetro.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val retro = response.body<RetrospectiveDTO>()
        assertEquals(createdRetro.id, retro.id)
    }

    @Test
    fun `POST add-note should add note to retrospective`() = runBlocking {
        // Given
        val token = registerUser("retro5@example.com", "Retro User 5")

        // Create a note ready for discussion
        val noteResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Note for retro", mood = null))
        }
        val note = noteResponse.body<NoteDTO>()

        // Mark note as ready for discussion
        client.patch("$baseUrl/api/notes/${note.id}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(UpdateNoteRequest(status = "ready_for_discussion"))
        }

        // Create retrospective
        val retroResponse = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }
        val retro = retroResponse.body<RetrospectiveDTO>()

        // When
        val addResponse = client.post("$baseUrl/api/retrospectives/${retro.id}/add-note") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(AddNoteToRetroRequest(noteId = note.id))
        }

        // Then
        assertEquals(HttpStatusCode.OK, addResponse.status)

        // Verify note is in retrospective
        val notesResponse = client.get("$baseUrl/api/retrospectives/${retro.id}/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val retroWithNotes = notesResponse.body<RetrospectiveWithNotesDTO>()
        assertTrue(retroWithNotes.notes.isNotEmpty())
        assertTrue(retroWithNotes.notes.any { it.id == note.id })
    }

    @Test
    fun `POST add-note should fail for draft notes`() = runBlocking {
        // Given
        val token = registerUser("retro6@example.com", "Retro User 6")

        // Create a draft note
        val noteResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Draft note", mood = null))
        }
        val note = noteResponse.body<NoteDTO>()

        // Create retrospective
        val retroResponse = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }
        val retro = retroResponse.body<RetrospectiveDTO>()

        // When - try to add draft note
        val response = client.post("$baseUrl/api/retrospectives/${retro.id}/add-note") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(AddNoteToRetroRequest(noteId = note.id))
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET retrospective notes should return notes in retro`() = runBlocking {
        // Given
        val token = registerUser("retro7@example.com", "Retro User 7")

        // Create and add note
        val noteResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Retro note", mood = null))
        }
        val note = noteResponse.body<NoteDTO>()

        client.patch("$baseUrl/api/notes/${note.id}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(UpdateNoteRequest(status = "ready_for_discussion"))
        }

        val retroResponse = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }
        val retro = retroResponse.body<RetrospectiveDTO>()

        client.post("$baseUrl/api/retrospectives/${retro.id}/add-note") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(AddNoteToRetroRequest(noteId = note.id))
        }

        // When
        val response = client.get("$baseUrl/api/retrospectives/${retro.id}/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val retroWithNotes = response.body<RetrospectiveWithNotesDTO>()
        assertTrue(retroWithNotes.notes.isNotEmpty())
        assertTrue(retroWithNotes.notes.any { it.id == note.id })
    }

    @Test
    fun `POST complete should finalize retrospective`(): Unit = runBlocking {
        // Given
        val token = registerUser("retro8@example.com", "Retro User 8")

        val retroResponse = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest())
        }
        val retro = retroResponse.body<RetrospectiveDTO>()

        // When
        val completeResponse = client.post("$baseUrl/api/retrospectives/${retro.id}/complete") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CompleteRetroRequest(finalSummary = "We discussed important topics"))
        }

        // Then
        assertEquals(HttpStatusCode.OK, completeResponse.status)

        // Verify status changed
        val getResponse = client.get("$baseUrl/api/retrospectives/${retro.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val completedRetro = getResponse.body<RetrospectiveDTO>()
        assertEquals("completed", completedRetro.status)
        assertEquals("We discussed important topics", completedRetro.finalSummary)
        assertNotNull(completedRetro.completedAt)
    }

    @Test
    fun `PATCH cancel should cancel scheduled retrospective`() = runBlocking {
        // Given
        val token = registerUser("retro9@example.com", "Retro User 9")

        val retroResponse = client.post("$baseUrl/api/retrospectives") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateRetrospectiveRequest(scheduledDate = "2024-12-25T10:00:00"))
        }
        val retro = retroResponse.body<RetrospectiveDTO>()

        // When
        val cancelResponse = client.patch("$baseUrl/api/retrospectives/${retro.id}/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, cancelResponse.status)

        // Verify status changed
        val getResponse = client.get("$baseUrl/api/retrospectives/${retro.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val cancelledRetro = getResponse.body<RetrospectiveDTO>()
        assertEquals("cancelled", cancelledRetro.status)
    }
}
