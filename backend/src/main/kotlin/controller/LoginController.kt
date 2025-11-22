package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.service.AuthService
import org.koin.ktor.ext.inject

fun Route.authRouting() {
    val authService by inject<AuthService>()
    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request.email, request.password)
            call.respond(response)
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()
            val response = authService.create(request.email, request.name, request.password)
            call.respond(response)
        }

        authenticate("jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.get("username") ?: throw IllegalStateException("No username in token")
                call.respond(mapOf("email" to username))
            }
        }
    }
}