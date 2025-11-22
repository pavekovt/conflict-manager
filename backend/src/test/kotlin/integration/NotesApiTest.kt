package me.pavekovt.integration

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.CreateNoteRequest
import me.pavekovt.dto.exchange.RegisterRequest
import me.pavekovt.dto.exchange.UpdateNoteRequest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class NotesApiTest : IntegrationTestBase() {

    private suspend fun registerAndLogin(): String {
        val registerRequest = RegisterRequest(
            email = "${UUID.randomUUID()}@example.com",
            name = "Notes Tester",
            password = "password123"
        )
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        val authResponse = response.body<AuthResponse>()
        return authResponse.token
    }

    @Test
    fun `POST notes should create note with authentication`(): Unit = runBlocking {
        // Given
        val token = registerAndLogin()
        val createRequest = CreateNoteRequest(
            content = "This is a test note",
            mood = "frustrated"
        )

        // When
        val response = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(createRequest)
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val note = response.body<NoteDTO>()
        assertNotNull(note.id)
        assertEquals("This is a test note", note.content)
        assertEquals("draft", note.status) // Default status
        assertEquals("frustrated", note.mood)
        assertNotNull(note.userId)
        assertNotNull(note.createdAt)
    }

    @Test
    fun `POST notes should return 401 without authentication`() = runBlocking {
        // Given
        val createRequest = CreateNoteRequest(content = "Test note")

        // When
        val response = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            setBody(createRequest)
        }

        // Then
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST notes should return 400 for blank content`() = runBlocking {
        // Given
        val token = registerAndLogin()
        val createRequest = CreateNoteRequest(content = "   ")

        // When
        val response = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(createRequest)
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET notes should return user's notes`() = runBlocking {
        // Given
        val token = registerAndLogin()

        // Create some notes
        client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Note 1"))
        }
        client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Note 2"))
        }

        // When
        val response = client.get("$baseUrl/api/notes") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val notes = response.body<List<NoteDTO>>()
        assertTrue(notes.size >= 2)
        assertTrue(notes.any { it.content == "Note 1" })
        assertTrue(notes.any { it.content == "Note 2" })
    }

    @Test
    fun `GET notes with status filter should return only matching notes`() = runBlocking {
        // Given
        val token = registerAndLogin()

        // Create a draft note
        val draftResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Draft note"))
        }
        val draftNote = draftResponse.body<NoteDTO>()

        // Update note to ready_for_discussion
        client.patch("$baseUrl/api/notes/${draftNote.id}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(UpdateNoteRequest(status = "ready_for_discussion"))
        }

        // When - get only ready_for_discussion notes
        val response = client.get("$baseUrl/api/notes?status=ready_for_discussion") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val notes = response.body<List<NoteDTO>>()
        assertTrue(notes.all { it.status == "ready_for_discussion" })
    }

    @Test
    fun `GET note by id should return the note`() = runBlocking {
        // Given
        val token = registerAndLogin()

        val createResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Specific note"))
        }
        val createdNote = createResponse.body<NoteDTO>()

        // When
        val response = client.get("$baseUrl/api/notes/${createdNote.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val note = response.body<NoteDTO>()
        assertEquals(createdNote.id, note.id)
        assertEquals("Specific note", note.content)
    }

    @Test
    fun `GET note by id should return 404 for non-existent note`() = runBlocking {
        // Given
        val token = registerAndLogin()

        // When
        val response = client.get("$baseUrl/api/notes/00000000-0000-0000-0000-000000000000") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH note should update content and status`() = runBlocking {
        // Given
        val token = registerAndLogin()

        val createResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Original content"))
        }
        val createdNote = createResponse.body<NoteDTO>()

        // When
        val updateResponse = client.patch("$baseUrl/api/notes/${createdNote.id}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(UpdateNoteRequest(
                content = "Updated content",
                status = "ready_for_discussion",
                mood = "neutral"
            ))
        }

        // Then
        assertEquals(HttpStatusCode.OK, updateResponse.status)

        val updatedNote = updateResponse.body<NoteDTO>()
        assertEquals("Updated content", updatedNote.content)
        assertEquals("ready_for_discussion", updatedNote.status)
        assertEquals("neutral", updatedNote.mood)
    }

    @Test
    fun `DELETE note should remove the note`() = runBlocking {
        // Given
        val token = registerAndLogin()

        val createResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(CreateNoteRequest(content = "Note to delete"))
        }
        val createdNote = createResponse.body<NoteDTO>()

        // When
        val deleteResponse = client.delete("$baseUrl/api/notes/${createdNote.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Then
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify note is deleted
        val getResponse = client.get("$baseUrl/api/notes/${createdNote.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `user should not be able to access another user's notes`() = runBlocking {
        // Given - User 1 creates a note
        val user1Request = RegisterRequest(
            email = "user1@example.com",
            name = "User 1",
            password = "password123"
        )
        val user1Response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(user1Request)
        }
        val user1Auth = user1Response.body<AuthResponse>()

        val createResponse = client.post("$baseUrl/api/notes") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${user1Auth.token}")
            setBody(CreateNoteRequest(content = "User 1's private note"))
        }
        val user1Note = createResponse.body<NoteDTO>()

        // User 2 registers
        val user2Request = RegisterRequest(
            email = "user2@example.com",
            name = "User 2",
            password = "password123"
        )
        val user2Response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(user2Request)
        }
        val user2Auth = user2Response.body<AuthResponse>()

        // When - User 2 tries to access User 1's note
        val response = client.get("$baseUrl/api/notes/${user1Note.id}") {
            header(HttpHeaders.Authorization, "Bearer ${user2Auth.token}")
        }

        // Then - Should return 404 (privacy: act like it doesn't exist)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
