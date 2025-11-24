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
