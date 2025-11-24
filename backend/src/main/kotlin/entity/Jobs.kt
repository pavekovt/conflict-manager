package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Tracks async background jobs for AI processing.
 * Used for reliability and observability of background work.
 */
object Jobs : UUIDTable("jobs") {
    val jobType = enumerationByName<JobType>("job_type", 50)
    val status = enumerationByName<JobStatus>("status", 20).default(JobStatus.PENDING)
    val entityId = uuid("entity_id") // ID of the entity being processed (conflict, feeling, retro)
    val payload = text("payload").nullable() // JSON payload with job-specific data
    val errorMessage = text("error_message").nullable() // Error details if job failed
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val startedAt = datetime("started_at").nullable()
    val completedAt = datetime("completed_at").nullable()
    val retryCount = integer("retry_count").default(0)
}

enum class JobType {
    PROCESS_FEELINGS,       // Process user feelings and generate AI guidance
    GENERATE_SUMMARY,       // Generate conflict resolution summary from both resolutions
    GENERATE_DISCUSSION_POINTS  // Generate retrospective discussion points
}

enum class JobStatus {
    PENDING,      // Job queued, waiting to be processed
    PROCESSING,   // Job is currently being processed
    COMPLETED,    // Job completed successfully
    FAILED        // Job failed (after retries)
}
