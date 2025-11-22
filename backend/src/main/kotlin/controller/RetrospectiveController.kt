package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.service.RetrospectiveService
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.retrospectiveRouting() {
    val retroService by inject<RetrospectiveService>()

    authenticate("jwt") {
        route("/retrospectives") {
            // Create retrospective
            post {
                val currentUserId = call.getCurrentUserId()
                val request = call.receive<CreateRetrospectiveRequest>()

                // If userIds provided, use those; otherwise default to current user only
                val userIds = request.userIds?.map { UUID.fromString(it) } ?: listOf(currentUserId)

                val retro = retroService.create(request.scheduledDate, userIds)
                call.respond(retro)
            }

            // Get my retrospectives (only ones where I'm a participant)
            get {
                val userId = call.getCurrentUserId()
                val retros = retroService.findByUser(userId)
                call.respond(retros)
            }

            // Get retrospective by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val retro = retroService.findById(retroId, userId)
                call.respond(retro)
            }

            // Get retrospective with notes
            get("/{id}/notes") {
                val userId = call.getCurrentUserId()
                val retroId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val retro = retroService.findByIdWithNotes(retroId, userId)
                call.respond(retro)
            }

            // Add note to retrospective
            post("/{id}/add-note") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val request = call.receive<AddNoteToRetroRequest>()

                retroService.addNote(retroId, UUID.fromString(request.noteId), userId)
                call.respond(mapOf("success" to true))
            }

            // Generate AI discussion points
            post("/{id}/generate-points") {
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                retroService.generateDiscussionPoints(retroId)
                call.respond(mapOf("success" to true))
            }

            // Complete retrospective
            post("/{id}/complete") {
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val request = call.receive<CompleteRetrospectiveRequest>()

                retroService.complete(retroId, request.finalSummary)
                call.respond(mapOf("success" to true))
            }

            // Cancel retrospective
            patch("/{id}/cancel") {
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                retroService.cancel(retroId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
