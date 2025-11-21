package me.pavekovt.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.pavekovt.ai.AIProvider
import me.pavekovt.ai.RetroPointsResult
import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.entity.NoteStatus
import me.pavekovt.entity.RetroStatus
import me.pavekovt.repository.NoteRepository
import me.pavekovt.repository.RetrospectiveRepository
import java.util.UUID
import kotlin.test.*

class RetrospectiveServiceTest {

    private lateinit var retrospectiveRepository: RetrospectiveRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var aiProvider: AIProvider
    private lateinit var retrospectiveService: RetrospectiveService

    private val userId = UUID.randomUUID()
    private val retroId = UUID.randomUUID()
    private val noteId = UUID.randomUUID()

    @BeforeTest
    fun setup() {
        retrospectiveRepository = mockk()
        noteRepository = mockk()
        aiProvider = mockk()
        retrospectiveService = RetrospectiveService(
            retrospectiveRepository,
            noteRepository,
            aiProvider
        )
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `create should create scheduled retrospective when date is provided`() = runBlocking {
        // Given
        val scheduledDate = "2024-12-25T10:00:00"
        val userIds = listOf(userId)
        val expectedRetro = RetrospectiveDTO(
            id = retroId.toString(),
            scheduledDate = scheduledDate,
            startedAt = "2024-01-01T00:00:00",
            completedAt = null,
            status = "scheduled",
            aiDiscussionPoints = null,
            finalSummary = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery {
            retrospectiveRepository.create(any(), RetroStatus.SCHEDULED, userIds)
        } returns expectedRetro

        // When
        val result = retrospectiveService.create(scheduledDate, userIds)

        // Then
        assertEquals(expectedRetro, result)
        assertEquals("scheduled", result.status)
        coVerify { retrospectiveRepository.create(any(), RetroStatus.SCHEDULED, userIds) }
    }

    @Test
    fun `create should create in-progress retrospective when date is null`() = runBlocking {
        // Given
        val userIds = listOf(userId)
        val expectedRetro = RetrospectiveDTO(
            id = retroId.toString(),
            scheduledDate = null,
            startedAt = "2024-01-01T00:00:00",
            completedAt = null,
            status = "in_progress",
            aiDiscussionPoints = null,
            finalSummary = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery {
            retrospectiveRepository.create(null, RetroStatus.IN_PROGRESS, userIds)
        } returns expectedRetro

        // When
        val result = retrospectiveService.create(null, userIds)

        // Then
        assertEquals(expectedRetro, result)
        assertEquals("in_progress", result.status)
        coVerify { retrospectiveRepository.create(null, RetroStatus.IN_PROGRESS, userIds) }
    }

    @Test
    fun `findByUser should return user's retrospectives`() = runBlocking {
        // Given
        val retrospectives = listOf(
            RetrospectiveDTO(
                id = retroId.toString(),
                scheduledDate = null,
                startedAt = "2024-01-01T00:00:00",
                completedAt = null,
                status = "in_progress",
                aiDiscussionPoints = null,
                finalSummary = null,
                createdAt = "2024-01-01T00:00:00"
            )
        )

        coEvery { retrospectiveRepository.findByUser(userId) } returns retrospectives

        // When
        val result = retrospectiveService.findByUser(userId)

        // Then
        assertEquals(retrospectives, result)
        coVerify { retrospectiveRepository.findByUser(userId) }
    }

    @Test
    fun `findById should return retrospective when user has access`() = runBlocking {
        // Given
        val retro = RetrospectiveDTO(
            id = retroId.toString(),
            scheduledDate = null,
            startedAt = "2024-01-01T00:00:00",
            completedAt = null,
            status = "in_progress",
            aiDiscussionPoints = null,
            finalSummary = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { retrospectiveRepository.findById(retroId) } returns retro
        coEvery { retrospectiveRepository.userHasAccessToRetro(retroId, userId) } returns true

        // When
        val result = retrospectiveService.findById(retroId, userId)

        // Then
        assertEquals(retro, result)
        coVerify { retrospectiveRepository.findById(retroId) }
        coVerify { retrospectiveRepository.userHasAccessToRetro(retroId, userId) }
    }

    @Test
    fun `findById should throw when user does not have access`() = runBlocking {
        // Given
        val retro = RetrospectiveDTO(
            id = retroId.toString(),
            scheduledDate = null,
            startedAt = "2024-01-01T00:00:00",
            completedAt = null,
            status = "in_progress",
            aiDiscussionPoints = null,
            finalSummary = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { retrospectiveRepository.findById(retroId) } returns retro
        coEvery { retrospectiveRepository.userHasAccessToRetro(retroId, userId) } returns false

        // When/Then
        assertFailsWith<IllegalStateException> {
            retrospectiveService.findById(retroId, userId)
        }
        coVerify { retrospectiveRepository.findById(retroId) }
        coVerify { retrospectiveRepository.userHasAccessToRetro(retroId, userId) }
    }

    @Test
    fun `addNote should add note when user owns it and note is ready`() = runBlocking {
        // Given
        val note = NoteDTO(
            id = noteId.toString(),
            userId = userId.toString(),
            content = "Test note",
            status = "ready_for_discussion",
            mood = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { noteRepository.findById(noteId) } returns note
        coEvery { retrospectiveRepository.addNote(retroId, noteId) } returns true

        // When
        retrospectiveService.addNote(retroId, noteId, userId)

        // Then
        coVerify { noteRepository.findById(noteId) }
        coVerify { retrospectiveRepository.addNote(retroId, noteId) }
    }

    @Test
    fun `addNote should throw when note not found`() = runBlocking {
        // Given
        coEvery { noteRepository.findById(noteId) } returns null

        // When/Then
        assertFailsWith<IllegalStateException> {
            retrospectiveService.addNote(retroId, noteId, userId)
        }
        coVerify { noteRepository.findById(noteId) }
        coVerify(exactly = 0) { retrospectiveRepository.addNote(any(), any()) }
    }

    @Test
    fun `addNote should throw when user does not own note`() = runBlocking {
        // Given
        val differentUserId = UUID.randomUUID()
        val note = NoteDTO(
            id = noteId.toString(),
            userId = differentUserId.toString(),
            content = "Test note",
            status = "ready_for_discussion",
            mood = null,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { noteRepository.findById(noteId) } returns note

        // When/Then
        assertFailsWith<IllegalStateException> {
            retrospectiveService.addNote(retroId, noteId, userId)
        }
        coVerify { noteRepository.findById(noteId) }
        coVerify(exactly = 0) { retrospectiveRepository.addNote(any(), any()) }
    }

    @Test
    fun `addNote should throw when note is not ready for discussion`() = runBlocking {
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

        // When/Then
        assertFailsWith<IllegalStateException> {
            retrospectiveService.addNote(retroId, noteId, userId)
        }
        coVerify { noteRepository.findById(noteId) }
        coVerify(exactly = 0) { retrospectiveRepository.addNote(any(), any()) }
    }

    @Test
    fun `generateDiscussionPoints should throw when no notes exist`() = runBlocking {
        // Given
        val retroWithNoNotes = RetrospectiveWithNotesDTO(
            id = retroId.toString(),
            scheduledDate = null,
            startedAt = "2024-01-01T00:00:00",
            completedAt = null,
            status = "in_progress",
            aiDiscussionPoints = null,
            finalSummary = null,
            notes = emptyList(),
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { retrospectiveRepository.findByIdWithNotes(retroId) } returns retroWithNoNotes

        // When/Then
        assertFailsWith<IllegalStateException> {
            retrospectiveService.generateDiscussionPoints(retroId)
        }
        coVerify { retrospectiveRepository.findByIdWithNotes(retroId) }
        coVerify(exactly = 0) { aiProvider.generateRetroPoints(any()) }
    }

    @Test
    fun `cancel should successfully cancel retrospective`() = runBlocking {
        // Given
        coEvery { retrospectiveRepository.cancel(retroId) } returns true

        // When
        retrospectiveService.cancel(retroId)

        // Then
        coVerify { retrospectiveRepository.cancel(retroId) }
    }

    @Test
    fun `cancel should throw when retrospective not found`() = runBlocking {
        // Given
        coEvery { retrospectiveRepository.cancel(retroId) } returns false

        // When/Then
        assertFailsWith<IllegalStateException> {
            retrospectiveService.cancel(retroId)
        }
        coVerify { retrospectiveRepository.cancel(retroId) }
    }
}
