package me.pavekovt

import io.ktor.server.application.*
import io.ktor.server.routing.*
import me.pavekovt.controller.*

fun Application.configureRouting() {
    routing {
        route("/api/") {
            authRouting()
            noteRouting()
            conflictRouting()
            decisionRouting()
            retrospectiveRouting()
        }
    }
}
