package me.pavekovt

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import me.pavekovt.configuration.*

fun main(args: Array<String>) {
    embeddedServer(
        Netty,
        port = 8080, // This is the port to which Ktor is listening
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureFrameworks()
    configureSerialization()
    configureStatusPages()
    configureSecurity()
    configureDatabase()
    configureHTTP()
    configureOpenApi()
    configureSSE()
    configureRouting()
}
