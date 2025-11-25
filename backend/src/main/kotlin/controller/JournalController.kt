package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.exchange.CreateJournalEntryRequest
import me.pavekovt.dto.exchange.UpdateJournalEntryRequest
import me.pavekovt.facade.JournalFacade
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.journalRouting() {
    val journalFacade by inject<JournalFacade>()

    authenticate("jwt") {
        route("/journal") {
            // Create journal entry (starts as draft)
            post {
                val userId = call.getCurrentUserId()
                val request = call.receive<CreateJournalEntryRequest>()

                val journal = journalFacade.create(request.content, userId)
                call.respond(journal)
            }

            // Get my journal entries
            get {
                val userId = call.getCurrentUserId()
                val status = call.request.queryParameters["status"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val journals = journalFacade.findMyJournals(userId, status, limit, offset)
                call.respond(journals)
            }

            // Get specific journal entry
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val journalId = UUID.fromString(
                    call.parameters["id"] ?: throw IllegalArgumentException("Missing journal ID")
                )

                val journal = journalFacade.findById(journalId, userId)
                call.respond(journal)
            }

            // Update journal entry (only drafts)
            patch("/{id}") {
                val userId = call.getCurrentUserId()
                val journalId = UUID.fromString(
                    call.parameters["id"] ?: throw IllegalArgumentException("Missing journal ID")
                )
                val request = call.receive<UpdateJournalEntryRequest>()

                journalFacade.update(journalId, request.content, userId)
                call.respond(mapOf("success" to true))
            }

            // Complete journal entry (triggers AI processing on next AI call)
            patch("/{id}/complete") {
                val userId = call.getCurrentUserId()
                val journalId = UUID.fromString(
                    call.parameters["id"] ?: throw IllegalArgumentException("Missing journal ID")
                )

                journalFacade.complete(journalId, userId)
                call.respond(mapOf("success" to true))
            }

            // Archive journal entry
            patch("/{id}/archive") {
                val userId = call.getCurrentUserId()
                val journalId = UUID.fromString(
                    call.parameters["id"] ?: throw IllegalArgumentException("Missing journal ID")
                )

                journalFacade.archive(journalId, userId)
                call.respond(mapOf("success" to true))
            }

            // Delete journal entry
            delete("/{id}") {
                val userId = call.getCurrentUserId()
                val journalId = UUID.fromString(
                    call.parameters["id"] ?: throw IllegalArgumentException("Missing journal ID")
                )

                journalFacade.delete(journalId, userId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
