package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.facade.RetrospectiveFacade
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.retrospectiveRouting() {
    val retroFacade by inject<RetrospectiveFacade>()

    authenticate("jwt") {
        route("/retrospectives") {
            // Create retrospective
            post {
                val currentUserId = call.getCurrentUserId()
                val request = call.receive<CreateRetrospectiveRequest>()

                // Partnership is automatically handled in service (creates for both partners)
                val retro = retroFacade.create(request.scheduledDate, currentUserId)
                call.respond(retro)
            }

            // Get my retrospectives (only ones where I'm a participant)
            get {
                val userId = call.getCurrentUserId()
                val retros = retroFacade.findAll(userId)
                call.respond(retros)
            }

            // Get retrospective by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val retro = retroFacade.findById(retroId, userId)
                call.respond(retro)
            }

            // Get retrospective with notes
            get("/{id}/notes") {
                val userId = call.getCurrentUserId()
                val retroId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val retro = retroFacade.findByIdWithNotes(retroId, userId)
                call.respond(retro)
            }

            // Add note to retrospective
            post("/{id}/add-note") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val request = call.receive<AddNoteToRetroRequest>()

                retroFacade.addNote(retroId, UUID.fromString(request.noteId), userId)
                call.respond(mapOf("success" to true))
            }

            // Generate AI discussion points
            post("/{id}/generate-points") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                retroFacade.generateDiscussionPoints(retroId, userId)
                call.respond(mapOf("success" to true))
            }

            // Approve retrospective discussion points
            patch("/{id}/approve") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val request = call.receive<ApproveRetrospectiveRequest>()
                retroFacade.approve(retroId, userId, request.approvalText)
                call.respond(mapOf("success" to true))
            }

            // Complete retrospective
            post("/{id}/complete") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                val request = call.receive<CompleteRetrospectiveRequest>()

                retroFacade.complete(retroId, request.finalSummary, userId)
                call.respond(mapOf("success" to true))
            }

            // Cancel retrospective
            patch("/{id}/cancel") {
                val userId = call.getCurrentUserId()
                val retroId =
                    UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing retrospective ID"))
                retroFacade.cancel(retroId, userId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
