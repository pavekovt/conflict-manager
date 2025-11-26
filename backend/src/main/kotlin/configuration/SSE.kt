package me.pavekovt.configuration

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.sse.SSE
import me.pavekovt.service.JobProcessorService
import org.koin.ktor.ext.inject

fun Application.configureSSE() {
    install(SSE) {
    }

    // Eagerly load JobProcessorService to start background workers
    // Without this, the service is lazily loaded only when SSE connections are made
    val jobProcessorService by inject<JobProcessorService>()
    jobProcessorService // Access to trigger instantiation
}
