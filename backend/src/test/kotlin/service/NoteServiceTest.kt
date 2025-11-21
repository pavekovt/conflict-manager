package me.pavekovt.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.pavekovt.dto.NoteDTO
import me.pavekovt.entity.Mood
import me.pavekovt.entity.NoteStatus
import me.pavekovt.repository.NoteRepository
import java.util.UUID
import kotlin.test.*

class NoteServiceTest {

    private lateinit var noteRepository: NoteRepository
    private lateinit var noteService: NoteService

    private val userId = UUID.randomUUID()
    private val noteId = UUID.randomUUID()

    @BeforeTest
    fun setup() {
        noteRepository = mockk()
        noteService = NoteService(noteRepository)
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `create should successfully create note with mood`() = runBlocking {
        // Given
        val content = "Test note content"
        val mood = "frustrated"
        val expectedNote = NoteDTO(
            id = noteId.toString(),
            userId = userId.toString(),
            content = content,
            status = "draft",
            mood = mood,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { noteRepository.create(userId, content, Mood.FRUSTRATED) } returns expectedNote

        // When
        val result = noteService.create(userId, content, mood)

        // Then
        assertEquals(expectedNote, result)
        coVerify { noteRepository.create(userId, content, Mood.FRUSTRATED) }
    }

    @Test
    fun `create should successfully create note without mood`() = runBlocking {
        // Given
        val content = "Test note content"
        val expectedNote = NoteDTO(
            id = noteId.toString(),
            userId = userId.toString(),
            content = content,
            status = "draft",
            mood = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { noteRepository.create(userId, content, null) } returns expectedNote

        // When
        val result = noteService.create(userId, content, null)

        // Then
        assertEquals(expectedNote, result)
        coVerify { noteRepository.create(userId, content, null) }
    }

    @Test
    fun `create should throw IllegalArgumentException for blank content`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            noteService.create(userId, "   ", null)
        }
        coVerify(exactly = 0) { noteRepository.create(any(), any(), any()) }
    }

    @Test
    fun `create should throw IllegalArgumentException for invalid mood`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            noteService.create(userId, "Test content", "invalid_mood")
        }
        coVerify(exactly = 0) { noteRepository.create(any(), any(), any()) }
    }

    @Test
    fun `findById should return note when user owns it`() = runBlocking {
        // Given
        val note = NoteDTO(
            id = noteId.toString(),
            userId = userId.toString(),
            content = "Test note",
            status = "draft",
            mood = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { noteRepository.findById(noteId) } returns note

        // When
        val result = noteService.findById(noteId, userId)

        // Then
        assertEquals(note, result)
        coVerify { noteRepository.findById(noteId) }
    }

    @Test
    fun `findById should return null when user does not own note`() = runBlocking {
        // Given
        val differentUserId = UUID.randomUUID()
        val note = NoteDTO(
            id = noteId.toString(),
            userId = differentUserId.toString(),
            content = "Test note",
            status = "draft",
            mood = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { noteRepository.findById(noteId) } returns note

        // When
        val result = noteService.findById(noteId, userId)

        // Then
        assertNull(result)
        coVerify { noteRepository.findById(noteId) }
    }

    @Test
    fun `findById should return null when note does not exist`() = runBlocking {
        // Given
        coEvery { noteRepository.findById(noteId) } returns null

        // When
        val result = noteService.findById(noteId, userId)

        // Then
        assertNull(result)
        coVerify { noteRepository.findById(noteId) }
    }

    @Test
    fun `findByUser should return notes with status filter`() = runBlocking {
        // Given
        val status = "draft"
        val notes = listOf(
            NoteDTO(
                id = noteId.toString(),
                userId = userId.toString(),
                content = "Note 1",
                status = status,
                mood = null,
                createdAt = "2024-01-01T00:00:00"
            )
        )

        coEvery { noteRepository.findByUser(userId, NoteStatus.DRAFT) } returns notes

        // When
        val result = noteService.findByUser(userId, status)

        // Then
        assertEquals(notes, result)
        coVerify { noteRepository.findByUser(userId, NoteStatus.DRAFT) }
    }

    @Test
    fun `findByUser should return all notes when status is null`() = runBlocking {
        // Given
        val notes = listOf(
            NoteDTO(
                id = noteId.toString(),
                userId = userId.toString(),
                content = "Note 1",
                status = "draft",
                mood = null,
                createdAt = "2024-01-01T00:00:00"
            )
        )

        coEvery { noteRepository.findByUser(userId, null) } returns notes

        // When
        val result = noteService.findByUser(userId, null)

        // Then
        assertEquals(notes, result)
        coVerify { noteRepository.findByUser(userId, null) }
    }

    @Test
    fun `findByUser should throw IllegalArgumentException for invalid status`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            noteService.findByUser(userId, "invalid_status")
        }
        coVerify(exactly = 0) { noteRepository.findByUser(any(), any()) }
    }

    @Test
    fun `update should successfully update note`() = runBlocking {
        // Given
        val newContent = "Updated content"
        val updatedNote = NoteDTO(
            id = noteId.toString(),
            userId = userId.toString(),
            content = newContent,
            status = "ready_for_discussion",
            mood = "neutral",
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery {
            noteRepository.update(
                noteId,
                userId,
                newContent,
                NoteStatus.READY_FOR_DISCUSSION,
                Mood.NEUTRAL
            )
        } returns updatedNote

        // When
        val result = noteService.update(
            noteId,
            userId,
            newContent,
            "ready_for_discussion",
            "neutral"
        )

        // Then
        assertEquals(updatedNote, result)
        coVerify {
            noteRepository.update(
                noteId,
                userId,
                newContent,
                NoteStatus.READY_FOR_DISCUSSION,
                Mood.NEUTRAL
            )
        }
    }

    @Test
    fun `update should throw IllegalStateException when note not found`(): Unit = runBlocking {
        // Given
        coEvery { noteRepository.update(any(), any(), any(), any(), any()) } returns null

        // When/Then
        assertFailsWith<IllegalStateException> {
            noteService.update(noteId, userId, "New content", null, null)
        }
    }

    @Test
    fun `update should throw IllegalArgumentException for blank content`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            noteService.update(noteId, userId, "   ", null, null)
        }
        coVerify(exactly = 0) { noteRepository.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `delete should return true when note is deleted`() = runBlocking {
        // Given
        coEvery { noteRepository.delete(noteId, userId) } returns true

        // When
        val result = noteService.delete(noteId, userId)

        // Then
        assertTrue(result)
        coVerify { noteRepository.delete(noteId, userId) }
    }

    @Test
    fun `delete should return false when note not found or not owned`() = runBlocking {
        // Given
        coEvery { noteRepository.delete(noteId, userId) } returns false

        // When
        val result = noteService.delete(noteId, userId)

        // Then
        assertFalse(result)
        coVerify { noteRepository.delete(noteId, userId) }
    }
}
