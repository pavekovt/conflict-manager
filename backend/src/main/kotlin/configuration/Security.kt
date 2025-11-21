package me.pavekovt.configuration

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.property
import me.pavekovt.properties.AuthenticationProperties

fun Application.configureSecurity() {
    val authConfig = property<AuthenticationProperties>("jwt")

    authentication {
        jwt("jwt") {
            realm = authConfig.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(authConfig.secret))
                    .withAudience(authConfig.audience)
                    .withIssuer(authConfig.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(authConfig.audience)) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
