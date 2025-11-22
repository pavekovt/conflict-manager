package me.pavekovt.integration

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.dto.exchange.ErrorResponse
import me.pavekovt.dto.exchange.LoginRequest
import me.pavekovt.dto.exchange.RegisterRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthApiTest : IntegrationTestBase() {

    @Test
    fun `POST register should create new user and return token`(): Unit = runBlocking {
        // Given
        val registerRequest = RegisterRequest(
            email = "test@example.com",
            name = "Test User",
            password = "password123"
        )

        // When
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val authResponse = response.body<AuthResponse>()
        assertNotNull(authResponse.token)
        assertTrue(authResponse.token.isNotBlank())
        assertEquals(3600000, authResponse.expiresIn)
        assertNotNull(authResponse.user)
        assertEquals("test@example.com", authResponse.user.email)
        assertEquals("Test User", authResponse.user.name)
        assertNotNull(authResponse.user.id)
        assertNotNull(authResponse.user.createdAt)
    }

    @Test
    fun `POST register should return 409 when email already exists`() = runBlocking {
        // Given - first registration
        val registerRequest = RegisterRequest(
            email = "duplicate@example.com",
            name = "First User",
            password = "password123"
        )
        client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // When - second registration with same email
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Then
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("User with email already exists: ${registerRequest.email}", response.body<ErrorResponse>().error)
    }

    @Test
    fun `POST register should return 400 for invalid email`() = runBlocking {
        // Given
        val registerRequest = RegisterRequest(
            email = "invalid-email",
            name = "Test User",
            password = "password123"
        )

        // When
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Invalid email format", response.body<ErrorResponse>().error)
    }

    @Test
    fun `POST register should return 400 for short password`() = runBlocking {
        // Given
        val registerRequest = RegisterRequest(
            email = "test@example.com",
            name = "Test User",
            password = "short"
        )

        // When
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Password must be at least 8 characters", response.body<ErrorResponse>().error)
    }

    @Test
    fun `POST register should return 400 for short name`() = runBlocking {
        // Given
        val registerRequest = RegisterRequest(
            email = "test@example.com",
            name = "A",
            password = "password123"
        )

        // When
        val response = client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Name must be at least 2 characters", response.body<ErrorResponse>().error)
    }

    @Test
    fun `POST login should return token for valid credentials`() = runBlocking {
        // Given - register a user first
        val registerRequest = RegisterRequest(
            email = "login@example.com",
            name = "Login User",
            password = "password123"
        )
        client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // When - login
        val loginRequest = LoginRequest(
            email = "login@example.com",
            password = "password123"
        )
        val response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)

        val authResponse = response.body<AuthResponse>()
        assertNotNull(authResponse.token)
        assertTrue(authResponse.token.isNotBlank())
        assertNotNull(authResponse.user)
        assertEquals("login@example.com", authResponse.user.email)
        assertEquals("Login User", authResponse.user.name)
    }

    @Test
    fun `POST login should return 401 for wrong password`() = runBlocking {
        // Given - register a user
        val registerRequest = RegisterRequest(
            email = "wrongpass@example.com",
            name = "User",
            password = "correctpassword"
        )
        client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }

        // When - login with wrong password
        val loginRequest = LoginRequest(
            email = "wrongpass@example.com",
            password = "wrongpassword"
        )
        val response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        // Then
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST login should return 401 for non-existent user`() = runBlocking {
        // When
        val loginRequest = LoginRequest(
            email = "nonexistent@example.com",
            password = "password123"
        )
        val response = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        // Then
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
