package me.pavekovt.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.PartnerInviteRequest
import me.pavekovt.facade.PartnershipFacade
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.partnershipRouting() {
    val partnershipFacade by inject<PartnershipFacade>()

    authenticate("jwt") {
        route("/partnerships") {
            // Send partner invitation
            post("/invite") {
                val request = call.receive<PartnerInviteRequest>()
                val currentUserId = call.getCurrentUserId()
                val partnership = partnershipFacade.sendInvitation(request, currentUserId)
                call.respond(HttpStatusCode.Created, partnership)
            }

            // Get all invitations (sent and received)
            get("/invitations") {
                val currentUserId = call.getCurrentUserId()
                val invitations = partnershipFacade.getInvitations(currentUserId)
                call.respond(invitations)
            }

            // Accept invitation
            post("/{id}/accept") {
                val partnershipId = UUID.fromString(call.parameters["id"])
                val currentUserId = call.getCurrentUserId()
                val partnership = partnershipFacade.acceptInvitation(partnershipId, currentUserId)
                call.respond(partnership)
            }

            // Reject invitation
            post("/{id}/reject") {
                val partnershipId = UUID.fromString(call.parameters["id"])
                val currentUserId = call.getCurrentUserId()
                partnershipFacade.rejectInvitation(partnershipId, currentUserId)
                call.respond(HttpStatusCode.NoContent)
            }

            // Get current active partnership
            get("/current") {
                val currentUserId = call.getCurrentUserId()
                val partnership = partnershipFacade.getCurrentPartnership(currentUserId)
                if (partnership == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("message" to "No active partnership"))
                } else {
                    call.respond(partnership)
                }
            }

            // End current partnership
            delete("/current") {
                val currentUserId = call.getCurrentUserId()
                partnershipFacade.endPartnership(currentUserId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
