package me.pavekovt.dto

import kotlinx.serialization.Serializable
import me.pavekovt.entity.JobStatus
import me.pavekovt.entity.JobType

@Serializable
data class JobDTO(
    val id: String,
    val jobType: JobType,
    val status: JobStatus,
    val entityId: String,
    val payload: String?,
    val errorMessage: String?,
    val createdAt: String,
    val startedAt: String?,
    val completedAt: String?,
    val retryCount: Int
)

/**
 * Enhanced job status for user-facing endpoints
 */
@Serializable
data class JobStatusDTO(
    val id: String,
    val type: JobType,
    val status: JobStatus,
    val progress: Int,  // 0-100 percentage
    val userMessage: String,
    val isComplete: Boolean,
    val isFailed: Boolean,
    val errorMessage: String? = null,
    val result: JobResult? = null,
    val createdAt: String,
    val estimatedCompletion: String? = null
)

@Serializable
data class JobResult(
    val resourceType: String,  // "conflict", "retrospective", "feelings"
    val resourceId: String,
    val actionUrl: String
)
