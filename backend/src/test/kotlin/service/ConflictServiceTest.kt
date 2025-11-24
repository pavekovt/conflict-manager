package me.pavekovt.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.pavekovt.ai.AIProvider
import me.pavekovt.ai.SummaryResult
import me.pavekovt.dto.AISummaryDTO
import me.pavekovt.dto.ConflictDTO
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.dto.ResolutionDTO
import me.pavekovt.entity.ConflictStatus
import me.pavekovt.repository.*
import java.util.UUID
import kotlin.test.*

class ConflictServiceTest {

    private lateinit var conflictRepository: ConflictRepository
    private lateinit var resolutionRepository: ResolutionRepository
    private lateinit var aiSummaryRepository: AISummaryRepository
    private lateinit var decisionRepository: DecisionRepository
    private lateinit var aiProvider: AIProvider
    private lateinit var conflictService: ConflictService

    private val userId = UUID.randomUUID()
    private val partnerId = UUID.randomUUID()
    private val conflictId = UUID.randomUUID()
    private val summaryId = UUID.randomUUID()

    @BeforeTest
    fun setup() {
        conflictRepository = mockk()
        resolutionRepository = mockk()
        aiSummaryRepository = mockk()
        decisionRepository = mockk()
        aiProvider = mockk()
        conflictService = ConflictService(
            conflictRepository,
            resolutionRepository,
            aiSummaryRepository,
            decisionRepository,
            aiProvider
        )
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `create should successfully create conflict`() = runBlocking {
        // Given
        val expectedConflict = ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = userId.toString(),
            status = ConflictStatus.PENDING_RESOLUTIONS,
            createdAt = "2024-01-01T00:00:00",
            myResolutionSubmitted = false,
            partnerResolutionSubmitted = false,
            summaryAvailable = false
        )

        coEvery { conflictRepository.create(userId) } returns expectedConflict

        // When
        val result = conflictService.create(userId)

        // Then
        assertEquals(expectedConflict, result)
        coVerify { conflictRepository.create(userId) }
    }

    @Test
    fun `findById should return conflict when user is initiator`() = runBlocking {
        // Given
        val conflict = ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = userId.toString(),
            status = ConflictStatus.PENDING_RESOLUTIONS,
            createdAt = "2024-01-01T00:00:00",
            myResolutionSubmitted = false,
            partnerResolutionSubmitted = false,
            summaryAvailable = false
        )

        coEvery { conflictRepository.findById(conflictId) } returns conflict

        // When
        val result = conflictService.findById(conflictId)

        // Then
        assertEquals(conflict, result)
        coVerify { conflictRepository.findById(conflictId) }
    }

    @Test
    fun `findById should return conflict when found`() = runBlocking {
        // Given
        val conflict = ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = partnerId.toString(),
            status = ConflictStatus.PENDING_RESOLUTIONS,
            createdAt = "2024-01-01T00:00:00",
            myResolutionSubmitted = false,
            partnerResolutionSubmitted = false,
            summaryAvailable = false
        )

        coEvery { conflictRepository.findById(conflictId) } returns conflict

        // When
        val result = conflictService.findById(conflictId)

        // Then
        assertEquals(conflict, result)
        coVerify { conflictRepository.findById(conflictId) }
    }

    @Test
    fun `findById should return null when conflict not found`() = runBlocking {
        // Given
        coEvery { conflictRepository.findById(conflictId) } returns null

        // When
        val result = conflictService.findById(conflictId)

        // Then
        assertNull(result)
        coVerify { conflictRepository.findById(conflictId) }
    }

    @Test
    fun `findByUser should return user's conflicts`() = runBlocking {
        // Given
        val conflicts = listOf(
            ConflictDTO(
                id = conflictId.toString(),
                initiatedBy = userId.toString(),
                status = ConflictStatus.PENDING_RESOLUTIONS,
                createdAt = "2024-01-01T00:00:00",
                myResolutionSubmitted = false,
                partnerResolutionSubmitted = false,
                summaryAvailable = false
            )
        )

        coEvery { conflictRepository.findByUser(userId, listOf()) } returns conflicts

        // When
        val result = conflictService.findByUser(userId, listOf())

        // Then
        assertEquals(conflicts, result)
        coVerify { conflictRepository.findByUser(userId, listOf()) }
    }


    @Test
    fun `submitResolution should throw when user already submitted`() = runBlocking {
        // Given
        coEvery { resolutionRepository.hasResolution(conflictId, userId) } returns true

        // When/Then
        assertFailsWith<IllegalStateException> {
            conflictService.submitResolution(conflictId, userId, "My resolution")
        }
        coVerify { resolutionRepository.hasResolution(conflictId, userId) }
        coVerify(exactly = 0) { resolutionRepository.create(any(), any(), any()) }
    }

    @Test
    fun `submitResolution should create resolution when only one submitted`() = runBlocking {
        // Given
        val resolutionText = "My resolution"
        val conflict = ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = userId.toString(),
            status = ConflictStatus.PENDING_RESOLUTIONS,
            createdAt = "2024-01-01T00:00:00",
            myResolutionSubmitted = true,
            partnerResolutionSubmitted = false,
            summaryAvailable = false
        )

        val createdResolution = ResolutionDTO(
            id = UUID.randomUUID().toString(),
            conflictId = conflictId.toString(),
            userId = userId.toString(),
            resolutionText = resolutionText,
            submittedAt = "2024-01-01T00:00:00"
        )

        coEvery { resolutionRepository.hasResolution(conflictId, userId) } returns false
        coEvery { resolutionRepository.create(conflictId, userId, resolutionText) } returns createdResolution
        coEvery { resolutionRepository.getBothResolutions(conflictId) } returns null
        coEvery { conflictRepository.findById(conflictId) } returns conflict

        // When
        val result = conflictService.submitResolution(conflictId, userId, resolutionText)

        // Then
        assertEquals(conflict, result)
        coVerify { resolutionRepository.create(conflictId, userId, resolutionText) }
        coVerify { resolutionRepository.getBothResolutions(conflictId) }
        coVerify(exactly = 0) { aiProvider.summarizeConflict(any(), any()) }
    }

    @Test
    fun `submitResolution should generate AI summary when both resolutions submitted`() = runBlocking {
        // Given
        val resolutionText = "My resolution"
        val resolution1Text = "Resolution 1"
        val resolution2Text = "Resolution 2"
        val createdResolution = ResolutionDTO(
            id = UUID.randomUUID().toString(),
            conflictId = conflictId.toString(),
            userId = userId.toString(),
            resolutionText = resolutionText,
            submittedAt = "2024-01-01T00:00:00"
        )
        val summaryResult = SummaryResult(
            summary = "AI generated summary",
            provider = "mock-ai"
        )
        val updatedConflict = ConflictDTO(
            id = conflictId.toString(),
            initiatedBy = userId.toString(),
            status = ConflictStatus.SUMMARY_GENERATED,
            createdAt = "2024-01-01T00:00:00",
            myResolutionSubmitted = true,
            partnerResolutionSubmitted = true,
            summaryAvailable = true
        )

        coEvery { resolutionRepository.hasResolution(conflictId, userId) } returns false
        coEvery { resolutionRepository.create(conflictId, userId, resolutionText) } returns createdResolution
        coEvery { resolutionRepository.getBothResolutions(conflictId) } returns Pair(resolution1Text, resolution2Text)
        coEvery { aiProvider.summarizeConflict(resolution1Text, resolution2Text) } returns summaryResult
        coEvery { aiSummaryRepository.create(conflictId, summaryResult.summary, summaryResult.provider) } returns summaryId
        coEvery { conflictRepository.updateStatus(conflictId, ConflictStatus.SUMMARY_GENERATED) } returns true
        coEvery { conflictRepository.findById(conflictId) } returns updatedConflict

        // When
        val result = conflictService.submitResolution(conflictId, userId, resolutionText)

        // Then
        assertEquals(updatedConflict, result)
        coVerify { resolutionRepository.create(conflictId, userId, resolutionText) }
        coVerify { resolutionRepository.getBothResolutions(conflictId) }
        coVerify { aiProvider.summarizeConflict(resolution1Text, resolution2Text) }
        coVerify { aiSummaryRepository.create(conflictId, summaryResult.summary, summaryResult.provider) }
        coVerify { conflictRepository.updateStatus(conflictId, ConflictStatus.SUMMARY_GENERATED) }
    }

    @Test
    fun `requestRefinement should update status successfully`() = runBlocking {
        // Given
        coEvery { conflictRepository.updateStatus(conflictId, ConflictStatus.REFINEMENT) } returns true

        // When
        conflictService.requestRefinement(conflictId)

        // Then
        coVerify { conflictRepository.updateStatus(conflictId, ConflictStatus.REFINEMENT) }
    }


    @Test
    fun `archive should update status successfully`() = runBlocking {
        // Given
        coEvery { conflictRepository.updateStatus(conflictId, ConflictStatus.ARCHIVED) } returns true

        // When
        conflictService.archive(conflictId)

        // Then
        coVerify { conflictRepository.updateStatus(conflictId, ConflictStatus.ARCHIVED) }
    }

}
