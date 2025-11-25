package me.pavekovt

import io.ktor.server.application.*
import io.ktor.server.routing.*
import me.pavekovt.controller.*
import me.pavekovt.service.JobProcessorService
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    routing {
        route("/api") {
            authRouting()
            userRouting()
            partnershipRouting()
            noteRouting()
            conflictRouting()
            decisionRouting()
            retrospectiveRouting()
            journalRouting()
            jobRouting()
            dashboardRouting()
            eventsRoutes()
        }
    }
}
