package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.JournalEntryDTO
import me.pavekovt.entity.JournalEntries
import me.pavekovt.entity.JournalStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class JournalRepositoryImpl : JournalRepository {

    override suspend fun create(userId: UUID, partnershipId: UUID, content: String): JournalEntryDTO = dbQuery {
        val journal = JournalEntries.insertReturning {
            it[JournalEntries.userId] = userId
            it[JournalEntries.partnershipId] = partnershipId
            it[JournalEntries.content] = content
            it[status] = JournalStatus.DRAFT
        }.single()

        journal.toJournalEntryDTO()
    }

    override suspend fun findById(id: UUID): JournalEntryDTO? = dbQuery {
        JournalEntries.selectAll()
            .where { JournalEntries.id eq id }
            .singleOrNull()
            ?.toJournalEntryDTO()
    }

    override suspend fun findByUser(
        userId: UUID,
        status: JournalStatus?,
        limit: Int,
        offset: Int
    ): List<JournalEntryDTO> = dbQuery {
        val query = if (status != null) {
            JournalEntries.selectAll()
                .where { (JournalEntries.userId eq userId) and (JournalEntries.status eq status) }
        } else {
            JournalEntries.selectAll()
                .where { JournalEntries.userId eq userId }
        }

        query.orderBy(JournalEntries.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toJournalEntryDTO() }
    }

    override suspend fun update(id: UUID, content: String): Boolean = dbQuery {
        JournalEntries.update({ JournalEntries.id eq id }) {
            it[JournalEntries.content] = content
        } > 0
    }

    override suspend fun updateStatus(id: UUID, status: JournalStatus): Boolean = dbQuery {
        JournalEntries.update({ JournalEntries.id eq id }) {
            it[JournalEntries.status] = status
        } > 0
    }

    override suspend fun complete(id: UUID): Boolean = dbQuery {
        JournalEntries.update({ JournalEntries.id eq id }) {
            it[status] = JournalStatus.COMPLETED
            it[completedAt] = CurrentDateTime
        } > 0
    }

    override suspend fun delete(id: UUID): Boolean = dbQuery {
        JournalEntries.deleteWhere { JournalEntries.id eq id } > 0
    }

    override suspend fun findUnprocessedByPartnership(partnershipId: UUID): List<JournalEntryDTO> = dbQuery {
        JournalEntries.selectAll()
            .where {
                (JournalEntries.partnershipId eq partnershipId) and
                (JournalEntries.status eq JournalStatus.COMPLETED)
            }
            .orderBy(JournalEntries.completedAt to SortOrder.ASC)
            .map { it.toJournalEntryDTO() }
    }

    override suspend fun markAsProcessed(journalIds: List<UUID>): Boolean = dbQuery {
        if (journalIds.isEmpty()) return@dbQuery true

        JournalEntries.update({ JournalEntries.id inList journalIds }) {
            it[status] = JournalStatus.AI_PROCESSED
        } > 0
    }
}

private fun ResultRow.toJournalEntryDTO() = JournalEntryDTO(
    id = this[JournalEntries.id].value.toString(),
    userId = this[JournalEntries.userId].value.toString(),
    partnershipId = this[JournalEntries.partnershipId].value.toString(),
    content = this[JournalEntries.content],
    status = this[JournalEntries.status].name.lowercase(),
    createdAt = this[JournalEntries.createdAt].toString(),
    completedAt = this[JournalEntries.completedAt]?.toString()
)
