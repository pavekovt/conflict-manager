package me.pavekovt.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.pavekovt.dto.UserDTO
import me.pavekovt.exception.UserAlreadyExistsException
import me.pavekovt.exception.WrongCredentialsException
import me.pavekovt.properties.AuthenticationProperties
import me.pavekovt.repository.UserRepository
import kotlin.test.*

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var authenticationProperties: AuthenticationProperties
    private lateinit var authService: AuthService

    @BeforeTest
    fun setup() {
        userRepository = mockk()
        authenticationProperties = AuthenticationProperties(
            secret = "test-secret-key-that-is-long-enough",
            audience = "test-audience",
            realm = "test-realm",
            issuer = "test-issuer"
        )
        authService = AuthService(userRepository, authenticationProperties)
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `create should successfully register new user`() = runBlocking {
        // Given
        val email = "test@example.com"
        val name = "Test User"
        val password = "password123"
        val expectedUser = UserDTO(
            id = "test-id",
            email = email,
            name = name,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { userRepository.findByEmail(email) } returns null
        coEvery { userRepository.create(email, any(), name) } returns expectedUser

        // When
        val result = authService.create(email, name, password)

        // Then
        assertNotNull(result.token)
        assertEquals(expectedUser, result.user)
        assertEquals(3600000, result.expiresIn)
        coVerify { userRepository.findByEmail(email) }
        coVerify { userRepository.create(email, any(), name) }
    }

    @Test
    fun `create should throw UserAlreadyExistsException when user exists`() = runBlocking {
        // Given
        val email = "existing@example.com"
        val name = "Test User"
        val password = "password123"
        val existingUser = UserDTO(
            id = "existing-id",
            email = email,
            name = name,
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { userRepository.findByEmail(email) } returns existingUser

        // When/Then
        assertFailsWith<UserAlreadyExistsException> {
            authService.create(email, name, password)
        }
        coVerify { userRepository.findByEmail(email) }
        coVerify(exactly = 0) { userRepository.create(any(), any(), any()) }
    }

    @Test
    fun `create should throw IllegalArgumentException for invalid email`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            authService.create("invalid-email", "Test User", "password123")
        }
        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `create should throw IllegalArgumentException for short name`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            authService.create("test@example.com", "A", "password123")
        }
        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `create should throw IllegalArgumentException for short password`() = runBlocking {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            authService.create("test@example.com", "Test User", "short")
        }
        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `login should successfully authenticate valid credentials`() = runBlocking {
        // Given
        val email = "test@example.com"
        val password = "password123"
        // Generate actual bcrypt hash for "password123"
        val passwordHash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
            .hashToString(12, password.toCharArray())
        val user = UserDTO(
            id = "test-id",
            email = email,
            name = "Test User",
            createdAt = "2024-01-01T00:00:00"
        )

        coEvery { userRepository.getUserPassword(email) } returns passwordHash
        coEvery { userRepository.findByEmail(email) } returns user

        // When
        val result = authService.login(email, password)

        // Then
        assertNotNull(result.token)
        assertEquals(user, result.user)
        assertEquals(3600000, result.expiresIn)
        coVerify { userRepository.getUserPassword(email) }
        coVerify { userRepository.findByEmail(email) }
    }

    @Test
    fun `login should throw WrongCredentialsException when user not found`() = runBlocking {
        // Given
        val email = "nonexistent@example.com"
        val password = "password123"

        coEvery { userRepository.getUserPassword(email) } returns null

        // When/Then
        assertFailsWith<WrongCredentialsException> {
            authService.login(email, password)
        }
        coVerify { userRepository.getUserPassword(email) }
        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
    }

    @Test
    fun `login should throw WrongCredentialsException for wrong password`() = runBlocking {
        // Given
        val email = "test@example.com"
        val correctPassword = "correctpassword"
        val wrongPassword = "wrongpassword"
        // Hash for a different password
        val passwordHash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
            .hashToString(12, correctPassword.toCharArray())

        coEvery { userRepository.getUserPassword(email) } returns passwordHash

        // When/Then
        assertFailsWith<WrongCredentialsException> {
            authService.login(email, wrongPassword)
        }
        coVerify { userRepository.getUserPassword(email) }
        coVerify(exactly = 0) { userRepository.findByEmail(any()) }
    }
}
