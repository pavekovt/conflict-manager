package me.pavekovt.repository

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import me.pavekovt.db.dbQuery
import me.pavekovt.dto.JobDTO
import me.pavekovt.entity.JobStatus
import me.pavekovt.entity.JobType
import me.pavekovt.entity.Jobs
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

class JobRepositoryImpl : JobRepository {

    private fun ResultRow.toJobDTO() = JobDTO(
        id = this[Jobs.id].value.toString(),
        jobType = this[Jobs.jobType],
        status = this[Jobs.status],
        entityId = this[Jobs.entityId].toString(),
        payload = this[Jobs.payload],
        errorMessage = this[Jobs.errorMessage],
        createdAt = this[Jobs.createdAt].toJavaLocalDateTime().toString(),
        startedAt = this[Jobs.startedAt]?.toJavaLocalDateTime()?.toString(),
        completedAt = this[Jobs.completedAt]?.toJavaLocalDateTime()?.toString(),
        retryCount = this[Jobs.retryCount]
    )

    override suspend fun create(
        jobType: JobType,
        entityId: UUID,
        payload: String?
    ): JobDTO = dbQuery {
        val resultRow = Jobs.insertReturning {
            it[Jobs.jobType] = jobType
            it[Jobs.entityId] = entityId
            it[Jobs.payload] = payload
        }.single()

        resultRow.toJobDTO()
    }

    override suspend fun findById(jobId: UUID): JobDTO? = dbQuery {
        Jobs.selectAll()
            .where { Jobs.id eq jobId }
            .singleOrNull()
            ?.toJobDTO()
    }

    override suspend fun findPendingJobs(limit: Int): List<JobDTO> = dbQuery {
        Jobs.selectAll()
            .where { Jobs.status eq JobStatus.PENDING }
            .orderBy(Jobs.createdAt)
            .limit(limit)
            .map { it.toJobDTO() }
    }

    override suspend fun updateStatus(
        jobId: UUID,
        status: JobStatus,
        errorMessage: String?
    ): JobDTO? = dbQuery {
        Jobs.update({ Jobs.id eq jobId }) {
            it[Jobs.status] = status
            if (errorMessage != null) {
                it[Jobs.errorMessage] = errorMessage
            }
        }

        findById(jobId)
    }

    override suspend fun markAsStarted(jobId: UUID): JobDTO? = dbQuery {
        val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()

        Jobs.update({ Jobs.id eq jobId }) {
            it[Jobs.status] = JobStatus.PROCESSING
            it[Jobs.startedAt] = now
        }

        findById(jobId)
    }

    override suspend fun markAsCompleted(jobId: UUID): JobDTO? = dbQuery {
        val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()

        Jobs.update({ Jobs.id eq jobId }) {
            it[Jobs.status] = JobStatus.COMPLETED
            it[Jobs.completedAt] = now
        }

        findById(jobId)
    }

    override suspend fun markAsFailed(jobId: UUID, errorMessage: String): JobDTO? = dbQuery {
        val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()

        Jobs.update({ Jobs.id eq jobId }) {
            it[Jobs.status] = JobStatus.FAILED
            it[Jobs.errorMessage] = errorMessage
            it[Jobs.completedAt] = now
        }

        findById(jobId)
    }

    override suspend fun incrementRetryCount(jobId: UUID): JobDTO? = dbQuery {
        // First get current count
        val currentJob = findById(jobId)
        val newCount = (currentJob?.retryCount ?: 0) + 1

        Jobs.update({ Jobs.id eq jobId }) {
            it[Jobs.retryCount] = newCount
        }

        findById(jobId)
    }
}
