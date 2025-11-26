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
import me.pavekovt.entity.NoteStatus
import me.pavekovt.integration.dsl.testApi
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

    @Test
    fun `POST retrospectives should create a manual retrospective`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()

            // When
            val retro = user.createRetrospective()

            // Then
            assertNotNull(retro.id)
            assertEquals("in_progress", retro.status)
            assertEquals(null, retro.scheduledDate)
            assertEquals(null, retro.completedAt)
        }
    }

    @Test
    fun `POST retrospectives should create scheduled retrospective`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()
            val scheduledDate = "2024-12-25T10:00"

            // When
            val retro = user.createRetrospective(scheduledDate)

            // Then
            assertEquals("scheduled", retro.status)
            assertEquals(scheduledDate, retro.scheduledDate)
        }
    }

    @Test
    fun `POST retrospectives with userIds should include specified users`() = runBlocking {
        utils.run {
            // Given
            val (user1, user2) = registerPartners()

            // When - User 1 creates retro including both users
            val retro = user1.createRetrospective(users = listOf(user2.id))

            // Then
            assertNotNull(retro.id)

            // Both users should see the retrospective
            val user2Retros = user2.getRetrospectives()
            assertTrue(user2Retros.any { it.id == retro.id })
        }
    }

    @Test
    fun `GET retrospectives should return user's retrospectives`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()
            user.createRetrospective()

            // When
            val retros = user.getRetrospectives()

            // Then
            assertTrue(retros.isNotEmpty())
        }
    }

    @Test
    fun `GET retrospective by id should return retro details`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()
            val createdRetro = user.createRetrospective()

            // When
            val retro = user.getRetrospective(createdRetro.id)

            // Then
            assertEquals(createdRetro.id, retro.id)
        }
    }

    @Test
    fun `POST add-note should add note to retrospective`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()

            // Create a note ready for discussion
            val note = user.createNote()
            user.patchNoteStatus(note.id, NoteStatus.READY_FOR_DISCUSSION)

            val retro = user.createRetrospective()

            // When
            user.addRetrospectiveNote(retro.id, note.id)

            // Then
            val retroWithNotes = user.getRetrospectiveNotes(retro.id)
            assertTrue(retroWithNotes.notes.isNotEmpty())
            assertTrue(retroWithNotes.notes.any { it.id == note.id })
        }
    }

    @Test
    fun `POST add-note should fail for draft notes`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()

            val note = user.createNote()
            val retro = user.createRetrospective()

            // When
            val response = user.addRetrospectiveNoteRaw(retro.id, note.id)

            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `POST complete should finalize retrospective`(): Unit = runBlocking {
        testApi(baseUrl, client) {
            partnership {
                users {
                    val id = user1.retrospective {
                        create()
                        approve()
                    }.returningId()

                    user2.retrospective {
                        fetch(id)
                        approve()
                        complete("Cool topics!")
                        assertState {
                            isCompleted()
                            hasSummary("Cool topics!")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `PATCH cancel should cancel scheduled retrospective`() = runBlocking {
        utils.run {
            // Given
            val (user) = registerPartners()
            val retro = user.createRetrospective()

            // When
            user.cancelRetro(retro.id)

            // Then
            val cancelledRetro = user.getRetrospective(retro.id)
            assertEquals("cancelled", cancelledRetro.status)
        }
    }
}
