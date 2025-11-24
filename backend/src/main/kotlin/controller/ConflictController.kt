package me.pavekovt.controller

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.facade.ConflictFacade
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.conflictRouting() {
    val conflictFacade by inject<ConflictFacade>()

    authenticate("jwt") {
        route("/conflicts") {
            // Create conflict
            post {
                val userId = call.getCurrentUserId()
                val conflict = conflictFacade.create(userId)
                call.respond(HttpStatusCode.Created, conflict)
            }

            // Get my conflicts
            get {
                val userId = call.getCurrentUserId()
                val conflicts = conflictFacade.findByUser(userId)
                call.respond(conflicts)
            }

            // Get conflict by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                val conflict = conflictFacade.findById(conflictId, userId)
                    ?: throw IllegalStateException("Conflict not found")
                call.respond(conflict)
            }

            // Submit feelings (FIRST step in conflict resolution)
            post("/{id}/feelings") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                val request = call.receive<SubmitFeelingsRequest>()

                val feelings = conflictFacade.submitFeelings(conflictId, userId, request.feelingsText)
                call.respond(HttpStatusCode.OK, feelings)
            }

            // Get my feelings for a conflict (to see AI guidance)
            get("/{id}/feelings") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                val feelings = conflictFacade.getFeelings(conflictId, userId)
                    ?: throw IllegalStateException("You haven't submitted your feelings for this conflict yet")
                call.respond(feelings)
            }

            // Submit resolution
            post("/{id}/resolutions") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                val request = call.receive<SubmitResolutionRequest>()

                val conflict = conflictFacade.submitResolution(conflictId, userId, request.resolutionText)
                call.respond(conflict)
            }

            // Get AI summary
            get("/{id}/summary") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                val summary = conflictFacade.getSummary(conflictId, userId)
                call.respond(summary)
            }

            // Approve summary
            post("/{id}/approve") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))

                val summary = conflictFacade.getSummary(conflictId, userId)
                conflictFacade.approveSummary(UUID.fromString(summary.id), userId, conflictId)

                call.respond(mapOf("success" to true))
            }

            // Request refinement
            post("/{id}/request-refinement") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                conflictFacade.requestRefinement(conflictId, userId)
                call.respond(mapOf("success" to true))
            }

            // Archive conflict
            post("/{id}/archive") {
                val userId = call.getCurrentUserId()
                val conflictId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
                conflictFacade.archive(conflictId, userId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
