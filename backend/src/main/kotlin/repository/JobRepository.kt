package me.pavekovt.repository

import me.pavekovt.dto.JobDTO
import me.pavekovt.entity.JobStatus
import me.pavekovt.entity.JobType
import java.util.*

interface JobRepository {
    suspend fun create(
        jobType: JobType,
        entityId: UUID,
        payload: String? = null
    ): JobDTO

    suspend fun findById(jobId: UUID): JobDTO?

    suspend fun findPendingJobs(limit: Int = 10): List<JobDTO>

    suspend fun updateStatus(
        jobId: UUID,
        status: JobStatus,
        errorMessage: String? = null
    ): JobDTO?

    suspend fun markAsStarted(jobId: UUID): JobDTO?

    suspend fun markAsCompleted(jobId: UUID): JobDTO?

    suspend fun markAsFailed(jobId: UUID, errorMessage: String): JobDTO?

    suspend fun incrementRetryCount(jobId: UUID): JobDTO?
}
