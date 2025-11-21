package me.pavekovt.controller

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import me.pavekovt.dto.exchange.LoginRequest
import me.pavekovt.dto.exchange.LoginResponse
import me.pavekovt.dto.exchange.RegisterRequest
import org.koin.ktor.ext.inject
import service.AuthService

fun Route.loginRouting() {
    val authService by inject<AuthService>()

    post("/login") {
        val user = call.receive<LoginRequest>()

        val login = authService.login(user.email, user.password)
        call.respond(LoginResponse(login))
    }

    post("/register") {
        val user = call.receive<RegisterRequest>()
        val login = authService.create(user.email, user.name, user.password)
        call.respond(LoginResponse(login))
    }

    authenticate("jwt") {
        get("/test") {
            val principal = call.principal<JWTPrincipal>()
            call.respond(principal?.get("username") ?: "not_found")
        }
    }
}