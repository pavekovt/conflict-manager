package me.pavekovt.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import me.pavekovt.dto.UserDTO
import me.pavekovt.dto.exchange.AuthResponse
import me.pavekovt.exception.UserAlreadyExistsException
import me.pavekovt.exception.WrongCredentialsException
import me.pavekovt.properties.AuthenticationProperties
import me.pavekovt.repository.UserRepository
import java.util.*

class AuthService(
    private val userRepository: UserRepository,
    private val authenticationProperties: AuthenticationProperties
) {

    suspend fun create(email: String, name: String, password: String): AuthResponse {
        require(email.isNotBlank() && email.contains("@")) { "Invalid email format" }
        require(name.isNotBlank() && name.length >= 2) { "Name must be at least 2 characters" }
        require(password.length >= 8) { "Password must be at least 8 characters" }

        val user = userRepository.findByEmail(email)
        if (user != null) {
            throw UserAlreadyExistsException(email)
        }

        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        val createdUser = userRepository.create(email, hash, name)
        val token = generateToken(createdUser.email)

        return AuthResponse(
            token = token,
            expiresIn = 3600000, // 1 hour
            user = createdUser
        )
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val passwordHash = userRepository.getUserPassword(email)
            ?: throw WrongCredentialsException()

        if (!BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified) {
            throw WrongCredentialsException()
        }

        val user = userRepository.findByEmail(email)
            ?: throw WrongCredentialsException()

        val token = generateToken(user.email)

        return AuthResponse(
            token = token,
            expiresIn = 3600000, // 1 hour
            user = user
        )
    }

    private fun generateToken(email: String): String {
        return JWT.create()
            .withAudience(authenticationProperties.audience)
            .withIssuer(authenticationProperties.issuer)
            .withClaim("username", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .sign(Algorithm.HMAC256(authenticationProperties.secret))
    }
}