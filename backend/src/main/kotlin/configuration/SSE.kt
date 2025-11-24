package me.pavekovt.configuration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.sse.SSE

fun Application.configureSSE() {
    install(SSE) {
    }
}
