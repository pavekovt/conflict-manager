package me.pavekovt.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.pavekovt.dto.DecisionDTO
import me.pavekovt.entity.DecisionStatus
import me.pavekovt.repository.DecisionRepository
import java.util.UUID
import kotlin.test.*

class DecisionServiceTest {

    private lateinit var decisionRepository: DecisionRepository
    private lateinit var decisionService: DecisionService

    private val decisionId = UUID.randomUUID()

    @BeforeTest
    fun setup() {
        decisionRepository = mockk()
        decisionService = DecisionService(decisionRepository)
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `create should successfully create decision with category`() = runBlocking {
        // Given
        val summary = "We decided to go with option A"
        val category = "technical"
        val expectedDecision = DecisionDTO(
            id = decisionId.toString(),
            conflictId = null,
            summary = summary,
            category = category,
            status = "pending",
            createdAt = "2024-01-01T00:00:00",
            reviewedAt = null
        )

        coEvery { decisionRepository.create(null, summary, category) } returns expectedDecision

        // When
        val result = decisionService.create(summary, category)

        // Then
        assertEquals(expectedDecision, result)
        coVerify { decisionRepository.create(null, summary, category) }
    }

    @Test
    fun `create should successfully create decision without category`() = runBlocking {
        // Given
        val summary = "We decided to go with option B"
        val expectedDecision = DecisionDTO(
            id = decisionId.toString(),
            conflictId = null,
            summary = summary,
            category = null,
            status = "pending",
            createdAt = "2024-01-01T00:00:00",
            reviewedAt = null
        )

        coEvery { decisionRepository.create(null, summary, null) } returns expectedDecision

        // When
        val result = decisionService.create(summary, null)

        // Then
        assertEquals(expectedDecision, result)
        coVerify { decisionRepository.create(null, summary, null) }
    }


    @Test
    fun `findAll should return all decisions when status is null`() = runBlocking {
        // Given
        val decisions = listOf(
            DecisionDTO(
                id = decisionId.toString(),
                conflictId = null,
                summary = "Decision 1",
                category = null,
                status = "pending",
                createdAt = "2024-01-01T00:00:00",
                reviewedAt = null
            )
        )

        coEvery { decisionRepository.findAll(null) } returns decisions

        // When
        val result = decisionService.findAll(null)

        // Then
        assertEquals(decisions, result)
        coVerify { decisionRepository.findAll(null) }
    }

    @Test
    fun `findAll should return filtered decisions when status is provided`() = runBlocking {
        // Given
        val status = "reviewed"
        val decisions = listOf(
            DecisionDTO(
                id = decisionId.toString(),
                conflictId = null,
                summary = "Decision 1",
                category = null,
                status = status,
                createdAt = "2024-01-01T00:00:00",
                reviewedAt = "2024-01-02T00:00:00"
            )
        )

        coEvery { decisionRepository.findAll(DecisionStatus.REVIEWED) } returns decisions

        // When
        val result = decisionService.findAll(DecisionStatus.REVIEWED)

        // Then
        assertEquals(decisions, result)
        coVerify { decisionRepository.findAll(DecisionStatus.REVIEWED) }
    }

    @Test
    fun `findById should return decision when it exists`() = runBlocking {
        // Given
        val decision = DecisionDTO(
            id = decisionId.toString(),
            conflictId = null,
            summary = "Test decision",
            category = null,
            status = "pending",
            createdAt = "2024-01-01T00:00:00",
            reviewedAt = null
        )

        coEvery { decisionRepository.findById(decisionId) } returns decision

        // When
        val result = decisionService.findById(decisionId)

        // Then
        assertEquals(decision, result)
        coVerify { decisionRepository.findById(decisionId) }
    }

    @Test
    fun `findById should throw NotFoundException when decision not found`() = runBlocking {
        // Given
        coEvery { decisionRepository.findById(decisionId) } returns null

        // When/Then
        assertFailsWith<me.pavekovt.exception.NotFoundException> {
            decisionService.findById(decisionId)
        }
        coVerify { decisionRepository.findById(decisionId) }
    }

    @Test
    fun `markReviewed should successfully mark decision as reviewed`() = runBlocking {
        // Given
        val reviewedDecision = DecisionDTO(
            id = decisionId.toString(),
            conflictId = null,
            summary = "Test decision",
            category = null,
            status = "reviewed",
            createdAt = "2024-01-01T00:00:00",
            reviewedAt = "2024-01-02T00:00:00"
        )

        coEvery { decisionRepository.markReviewed(decisionId) } returns true
        coEvery { decisionRepository.findById(decisionId) } returns reviewedDecision

        // When
        val result = decisionService.markReviewed(decisionId)

        // Then
        assertEquals(reviewedDecision, result)
        coVerify { decisionRepository.markReviewed(decisionId) }
        coVerify { decisionRepository.findById(decisionId) }
    }

    @Test
    fun `markReviewed should throw NotFoundException when decision not found`() = runBlocking {
        // Given
        coEvery { decisionRepository.markReviewed(decisionId) } returns false

        // When/Then
        assertFailsWith<me.pavekovt.exception.NotFoundException> {
            decisionService.markReviewed(decisionId)
        }
        coVerify { decisionRepository.markReviewed(decisionId) }
        coVerify(exactly = 0) { decisionRepository.findById(any()) }
    }

    @Test
    fun `archive should successfully archive decision`() = runBlocking {
        // Given
        val archivedDecision = DecisionDTO(
            id = decisionId.toString(),
            conflictId = null,
            summary = "Test decision",
            category = null,
            status = "archived",
            createdAt = "2024-01-01T00:00:00",
            reviewedAt = null
        )

        coEvery { decisionRepository.archive(decisionId) } returns true
        coEvery { decisionRepository.findById(decisionId) } returns archivedDecision

        // When
        val result = decisionService.archive(decisionId)

        // Then
        assertEquals(archivedDecision, result)
        coVerify { decisionRepository.archive(decisionId) }
        coVerify { decisionRepository.findById(decisionId) }
    }

    @Test
    fun `archive should throw NotFoundException when decision not found`() = runBlocking {
        // Given
        coEvery { decisionRepository.archive(decisionId) } returns false

        // When/Then
        assertFailsWith<me.pavekovt.exception.NotFoundException> {
            decisionService.archive(decisionId)
        }
        coVerify { decisionRepository.archive(decisionId) }
        coVerify(exactly = 0) { decisionRepository.findById(any()) }
    }
}
