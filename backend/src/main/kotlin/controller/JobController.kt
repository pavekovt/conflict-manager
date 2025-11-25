package me.pavekovt.controller

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.JobResult
import me.pavekovt.dto.JobStatusDTO
import me.pavekovt.entity.JobStatus
import me.pavekovt.entity.JobType
import me.pavekovt.repository.JobRepository
import me.pavekovt.utils.getCurrentUserId
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.jobRouting() {
    val jobRepository by inject<JobRepository>()

    authenticate("jwt") {
        route("/jobs") {
            // Get job status by ID
            get("/{id}") {
                val userId = call.getCurrentUserId()
                val jobId = UUID.fromString(
                    call.parameters["id"] ?: throw IllegalArgumentException("Missing job ID")
                )

                val job = jobRepository.findById(jobId)
                    ?: throw IllegalStateException("Job not found")

                // Convert to user-friendly status
                val status = JobStatusDTO(
                    id = job.id,
                    type = job.jobType,
                    status = job.status,
                    progress = when (job.status) {
                        JobStatus.PENDING -> 0
                        JobStatus.PROCESSING -> 50
                        JobStatus.COMPLETED -> 100
                        JobStatus.FAILED -> 0
                    },
                    userMessage = getUserMessage(job.jobType, job.status),
                    isComplete = job.status == JobStatus.COMPLETED,
                    isFailed = job.status == JobStatus.FAILED,
                    errorMessage = job.errorMessage,
                    result = if (job.status == JobStatus.COMPLETED) {
                        getJobResult(job.jobType, job.entityId)
                    } else null,
                    createdAt = job.createdAt,
                    estimatedCompletion = if (job.status in listOf(JobStatus.PENDING, JobStatus.PROCESSING)) {
                        // Simple estimation: 5 seconds from now
                        java.time.Instant.now().plusSeconds(5).toString()
                    } else null
                )

                call.respond(status)
            }
        }
    }
}

private fun getUserMessage(jobType: JobType, status: JobStatus): String {
    return when {
        status == JobStatus.COMPLETED -> when (jobType) {
            JobType.PROCESS_FEELINGS -> "Your feelings have been processed. AI guidance is ready!"
            JobType.GENERATE_SUMMARY -> "Conflict summary has been generated. Review and approve it."
            JobType.GENERATE_DISCUSSION_POINTS -> "Retrospective discussion points are ready."
            JobType.UPDATE_PARTNERSHIP_CONTEXT -> "Partnership context has been updated."
        }
        status == JobStatus.FAILED -> "Processing failed. Please try again or contact support."
        status == JobStatus.PROCESSING -> when (jobType) {
            JobType.PROCESS_FEELINGS -> "AI is processing your feelings..."
            JobType.GENERATE_SUMMARY -> "AI is generating the conflict summary..."
            JobType.GENERATE_DISCUSSION_POINTS -> "AI is generating discussion points..."
            JobType.UPDATE_PARTNERSHIP_CONTEXT -> "Updating relationship context..."
        }
        else -> "Processing queued, please wait..."
    }
}

private fun getJobResult(jobType: JobType, entityId: String): JobResult {
    return when (jobType) {
        JobType.PROCESS_FEELINGS -> JobResult(
            resourceType = "feelings",
            resourceId = entityId,
            actionUrl = "/api/conflicts/{conflictId}/feelings"
        )
        JobType.GENERATE_SUMMARY -> JobResult(
            resourceType = "conflict",
            resourceId = entityId,
            actionUrl = "/api/conflicts/$entityId/summary"
        )
        JobType.GENERATE_DISCUSSION_POINTS -> JobResult(
            resourceType = "retrospective",
            resourceId = entityId,
            actionUrl = "/api/retrospectives/$entityId"
        )
        JobType.UPDATE_PARTNERSHIP_CONTEXT -> JobResult(
            resourceType = "partnership",
            resourceId = entityId,
            actionUrl = "/api/partnerships/current"
        )
    }
}
