package me.pavekovt.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.pavekovt.utils.getCurrentUserId
import me.pavekovt.entity.JobType
import me.pavekovt.service.JobEvent
import me.pavekovt.service.JobProcessorService
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.getValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Server-Sent Events controller for streaming AI processing updates to clients
 */
fun Route.eventsRoutes() {
    val logger = LoggerFactory.getLogger("EventsController")
    val jobProcessorService by inject<JobProcessorService>()

    route("/events") {

        authenticate("jwt") {
            /**
             * SSE endpoint for streaming job events
             * GET /api/events/stream?entityId=xxx
             *
             * Streams real-time updates about AI processing jobs for a specific entity (conflict, feeling, retro).
             * Client can connect and listen for events like:
             * - Job started
             * - Job completed
             * - Job failed
             * - Job retrying
             */
            sse("/stream") {
                val userId = call.getCurrentUserId()
                val entityId = call.request.queryParameters["entityId"]

                if (entityId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing entityId parameter"))
                    return@sse
                }

                heartbeat {
                    period = 8.seconds
                    event = ServerSentEvent("heartbeat")
                }

                logger.info("User $userId connecting to SSE stream for entity $entityId")

                // Stream events for this entity
                jobProcessorService.eventFlow
                    .filter { event -> event.entityId == entityId }
                    .map { event -> event.toSSE() }
                    .collect { sseMessage ->
                        logger.debug("Sending SSE event: $sseMessage")
                        send(sseMessage)
                    }
            }

            /**
             * SSE endpoint for streaming all job events (admin/debugging)
             * GET /api/events/stream/all
             */
            sse("/stream/all") {
                val userId = call.getCurrentUserId()
                logger.info("User $userId connecting to full SSE stream")

//                heartbeat {
//                    period = 8.seconds
//                    event = ServerSentEvent("heartbeat")
//                }

                send(ServerSentEvent("Connected"))

                jobProcessorService.eventFlow
                    .map { event -> event.toSSE() }
                    .collect { sseMessage ->
                        logger.debug("Sending SSE event: $sseMessage")
                        send(sseMessage)
                    }
            }
        }
    }
}

/**
 * Convert JobEvent to SSE format
 * Format: "event: {eventType}\ndata: {json}\n\n"
 */
private fun JobEvent.toSSE(): String {
    val eventType = when (this) {
        is JobEvent.Started -> "job_started"
        is JobEvent.Completed -> "job_completed"
        is JobEvent.Failed -> "job_failed"
        is JobEvent.Retrying -> "job_retrying"
    }

    val data = when (this) {
        is JobEvent.Started -> SSEJobStarted(jobId, jobType, entityId)
        is JobEvent.Completed -> SSEJobCompleted(jobId, jobType, entityId)
        is JobEvent.Failed -> SSEJobFailed(jobId, jobType, entityId, errorMessage)
        is JobEvent.Retrying -> SSEJobRetrying(jobId, jobType, entityId, retryCount)
    }

    val json = Json.encodeToString(SSEData.serializer(), SSEData(eventType, data.toString()))
    return "event: $eventType\ndata: $json\n\n"
}

@Serializable
data class SSEData(val event: String, val data: String)

@Serializable
data class SSEJobStarted(val jobId: String, val jobType: JobType, val entityId: String)

@Serializable
data class SSEJobCompleted(val jobId: String, val jobType: JobType, val entityId: String)

@Serializable
data class SSEJobFailed(val jobId: String, val jobType: JobType, val entityId: String, val errorMessage: String)

@Serializable
data class SSEJobRetrying(val jobId: String, val jobType: JobType, val entityId: String, val retryCount: Int)
