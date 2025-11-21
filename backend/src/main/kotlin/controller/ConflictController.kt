package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.service.ConflictService
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.conflictRouting() {
    val conflictService by inject<ConflictService>()

    authenticate("jwt") {
        // Create conflict
        post("/api/conflicts") {
            val userId = call.getCurrentUserId()
            val conflict = conflictService.create(userId)
            call.respond(conflict)
        }

        // Get my conflicts
        get("/api/conflicts") {
            val userId = call.getCurrentUserId()
            val conflicts = conflictService.findByUser(userId)
            call.respond(conflicts)
        }

        // Get conflict by ID
        get("/api/conflicts/{id}") {
            val userId = call.getCurrentUserId()
            val conflictId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
            val conflict = conflictService.findById(conflictId, userId)
                ?: throw IllegalStateException("Conflict not found")
            call.respond(conflict)
        }

        // Submit resolution
        post("/api/conflicts/{id}/resolutions") {
            val userId = call.getCurrentUserId()
            val conflictId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
            val request = call.receive<SubmitResolutionRequest>()

            val conflict = conflictService.submitResolution(conflictId, userId, request.resolutionText)
            call.respond(conflict)
        }

        // Get AI summary
        get("/api/conflicts/{id}/summary") {
            val userId = call.getCurrentUserId()
            val conflictId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
            val summary = conflictService.getSummary(conflictId, userId)
            call.respond(summary)
        }

        // Approve summary
        post("/api/conflicts/{id}/approve") {
            val userId = call.getCurrentUserId()
            val conflictId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))

            val summary = conflictService.getSummary(conflictId, userId)
            conflictService.approveSummary(UUID.fromString(summary.id), userId, conflictId)

            call.respond(mapOf("success" to true))
        }

        // Request refinement
        post("/api/conflicts/{id}/request-refinement") {
            val userId = call.getCurrentUserId()
            val conflictId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
            conflictService.requestRefinement(conflictId, userId)
            call.respond(mapOf("success" to true))
        }

        // Archive conflict
        patch("/api/conflicts/{id}/archive") {
            val userId = call.getCurrentUserId()
            val conflictId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing conflict ID"))
            conflictService.archive(conflictId, userId)
            call.respond(mapOf("success" to true))
        }
    }
}
