package service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import me.pavekovt.expection.UserAlreadyExistsException
import me.pavekovt.expection.WrongCredentialsException
import me.pavekovt.properties.AuthenticationProperties
import repository.UserRepository
import java.util.*

class AuthService(
    private val userRepository: UserRepository,
    private val authenticationProperties: AuthenticationProperties
) {

    suspend fun create(email: String, name: String, password: String): String {
        val user = userRepository.findByEmail(email)
        if (user != null) {
            throw UserAlreadyExistsException(email)
        }

        require(email.isNotBlank() && email.contains("@")) { "Invalid email format" }
        require(name.isNotBlank() && name.length >= 2) { "Name must be at least 2 characters" }
        require(password.length >= 8) { "Password must be at least 8 characters" }

        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        userRepository.create(email, hash.toString(), name)
        return generateToken(email)
    }

    suspend fun login(email: String, password: String): String {
        val passwordHash = userRepository.getUserPassword(email)
            ?: throw WrongCredentialsException()

        if (!BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified) {
            throw WrongCredentialsException()
        }

        return generateToken(email)
    }

    private fun generateToken(email: String): String {
        return JWT.create()
            .withAudience(authenticationProperties.audience)
            .withIssuer(authenticationProperties.issuer)
            .withClaim("username", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 600000))
            .sign(Algorithm.HMAC256(authenticationProperties.secret))
    }
}