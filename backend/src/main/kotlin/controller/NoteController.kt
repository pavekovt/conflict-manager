package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.*
import me.pavekovt.exception.NotFoundException
import me.pavekovt.service.NoteService
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.noteRouting() {
    val noteService by inject<NoteService>()

    authenticate("jwt") {
        route("/notes") {
            // Create note
            post {
                val userId = call.getCurrentUserId()
                val request = call.receive<CreateNoteRequest>()
                val note = noteService.create(userId, request.content, request.mood)
                call.respond(note)
            }

            // Get my notes
            get {
                val userId = call.getCurrentUserId()
                val status = call.request.queryParameters["status"]
                val notes = noteService.findByUser(userId, status)
                call.respond(notes)
            }

            // Get note by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val noteId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing note ID"))
                val note = noteService.findById(noteId, userId)
                    ?: throw NotFoundException()
                call.respond(note)
            }

            // Update note
            patch("/{id}") {
                val userId = call.getCurrentUserId()
                val noteId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing note ID"))
                val request = call.receive<UpdateNoteRequest>()

                val note = noteService.update(noteId, userId, request.content, request.status, request.mood)
                call.respond(note)
            }

            // Delete note
            delete("/{id}") {
                val userId = call.getCurrentUserId()
                val noteId = UUID.fromString(call.parameters["id"] ?: throw IllegalArgumentException("Missing note ID"))
                val deleted = noteService.delete(noteId, userId)

                if (deleted) {
                    call.respond(mapOf("success" to true))
                } else {
                    throw IllegalStateException("Failed to delete note")
                }
            }
        }
    }
}
