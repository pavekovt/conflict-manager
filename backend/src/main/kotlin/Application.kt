package me.pavekovt

import configuration.configureStatusPages
import io.ktor.server.application.*
import me.pavekovt.configuration.configureDatabase
import me.pavekovt.configuration.configureFrameworks
import me.pavekovt.configuration.configureHTTP
import me.pavekovt.configuration.configureSecurity
import me.pavekovt.configuration.configureSerialization

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
