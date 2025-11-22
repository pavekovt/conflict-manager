package me.pavekovt.configuration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import me.pavekovt.dto.exchange.ErrorResponse
import me.pavekovt.exception.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<UserAlreadyExistsException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(cause.message ?: "User already exists")
            )
        }

        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(cause.message ?: "Not Found")
            )
        }

        exception<UserNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(cause.message ?: "Invalid email or password")
            )
        }

        exception<WrongCredentialsException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(cause.message ?: "Invalid email or password")
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Invalid request")
            )
        }

        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(cause.message ?: "Invalid operation")
            )
        }

        exception<Exception> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error")
            )
        }
    }
}
