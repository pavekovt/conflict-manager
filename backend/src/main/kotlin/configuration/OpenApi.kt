package me.pavekovt.configuration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.routing
import io.ktor.util.generateNonce

fun Application.configureOpenApi() {
    routing {
        openAPI(path="openapi") {}
    }
}
