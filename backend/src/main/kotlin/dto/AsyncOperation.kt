package me.pavekovt.dto

import kotlinx.serialization.Serializable
import me.pavekovt.entity.JobType

/**
 * Wrapper for async operation responses to provide job tracking information
 */
@Serializable
data class AsyncOperationResponse<T>(
    val data: T,
    val job: JobInfo
)

@Serializable
data class JobInfo(
    val id: String,
    val type: JobType,
    val status: String,
    val estimatedDuration: Int,  // seconds
    val pollUrl: String,
    val sseEventType: String
)
