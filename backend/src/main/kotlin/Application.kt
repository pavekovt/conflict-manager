package me.pavekovt

import io.ktor.server.application.*
import me.pavekovt.configuration.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureFrameworks()
    configureSerialization()
    configureStatusPages()
    configureSecurity()
    configureDatabase()
    configureHTTP()
    configureRouting()
}
