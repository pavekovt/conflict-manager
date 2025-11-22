package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.service.DecisionService
import me.pavekovt.repository.UserRepository
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.decisionRouting() {
    val decisionService by inject<DecisionService>()
    val userRepository by inject<UserRepository>()

    authenticate("jwt") {
        route("/decisions") {
            // Get all decisions
            get {
                val userId = call.getCurrentUserId()
                val status = call.request.queryParameters["status"]
                val decisions = decisionService.findAll(status, userId)
                call.respond(decisions)
            }

            // Get decision by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val decisionId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing decision ID"))
                val decision = decisionService.findById(decisionId, userId)
                call.respond(decision)
            }

            // Mark decision as reviewed
            patch("/{id}/review") {
                val userId = call.getCurrentUserId()
                val decisionId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing decision ID"))
                val decision = decisionService.markReviewed(decisionId, userId)
                call.respond(decision)
            }

            // Archive decision
            patch("/{id}/archive") {
                val userId = call.getCurrentUserId()
                val decisionId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing decision ID"))
                val decision = decisionService.archive(decisionId, userId)
                call.respond(decision)
            }

            // Create manual decision (not from conflict)
            post {
                val userId = call.getCurrentUserId()
                val request = call.receive<CreateDecisionRequest>()
                val decision = decisionService.create(request.summary, request.category, userId)
                call.respond(decision)
            }
        }
    }
}
