package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.exception.NotFoundException
import me.pavekovt.facade.NoteFacade
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.noteRouting() {
    val noteFacade by inject<NoteFacade>()

    authenticate("jwt") {
        route("/notes") {
            // Create note
            post {
                val userId = call.getCurrentUserId()
                val request = call.receive<CreateNoteRequest>()
                val note = noteFacade.create(request, userId)
                call.respond(note)
            }

            // Get my notes
            get {
                val userId = call.getCurrentUserId()
                val status = call.request.queryParameters["status"]
                val notes = noteFacade.findAll(status, userId)
                call.respond(notes)
            }

            // Get note by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val noteId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing note ID"))
                val note = noteFacade.findById(noteId, userId)
                call.respond(note)
            }

            // Update note
            patch("/{id}") {
                val userId = call.getCurrentUserId()
                val noteId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing note ID"))
                val request = call.receive<UpdateNoteRequest>()

                val note = noteFacade.update(noteId, request, userId)
                call.respond(note)
            }

            // Delete note
            delete("/{id}") {
                val userId = call.getCurrentUserId()
                val noteId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing note ID"))
                noteFacade.delete(noteId, userId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
