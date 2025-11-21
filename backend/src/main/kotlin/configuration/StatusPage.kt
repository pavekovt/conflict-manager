package configuration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import me.pavekovt.expection.UserAlreadyExistsException
import me.pavekovt.expection.WrongCredentialsException

data class ErrorResponse(val message: String)

fun Application.configureStatusPages() {
      install(StatusPages) {
          exception<UserAlreadyExistsException> { call, cause ->
              call.respond(
                  HttpStatusCode.Conflict,
                  ErrorResponse(cause.message ?: "User already exists"),
              )
          }

          exception<WrongCredentialsException> { call, cause ->
              call.respond(
                  HttpStatusCode.Unauthorized,
                  ErrorResponse("Invalid email or password")
              )
          }

          exception<IllegalArgumentException> { call, cause ->
              call.respond(
                  HttpStatusCode.BadRequest,
                  ErrorResponse(cause.message ?: "Invalid request")
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