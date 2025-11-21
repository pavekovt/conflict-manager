package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.entity.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID
import kotlinx.datetime.LocalDateTime

class RetrospectiveRepositoryImpl : RetrospectiveRepository {

    override suspend fun create(scheduledDate: LocalDateTime?, status: RetroStatus, userIds: List<UUID>): RetrospectiveDTO = dbQuery {
        val retro = Retrospectives.insertReturning {
            it[Retrospectives.scheduledDate] = scheduledDate
            it[Retrospectives.status] = status
        }.single()

        val retroId = retro[Retrospectives.id].value

        // Add users to retrospective
        userIds.forEach { userId ->
            RetrospectiveUsers.insert {
                it[retrospectiveId] = retroId
                it[RetrospectiveUsers.userId] = userId
            }
        }

        retro.toRetrospectiveDTO()
    }

    override suspend fun findById(id: UUID): RetrospectiveDTO? = dbQuery {
        Retrospectives.selectAll()
            .where { Retrospectives.id eq id }
            .singleOrNull()
            ?.toRetrospectiveDTO()
    }

    override suspend fun findByIdWithNotes(id: UUID): RetrospectiveWithNotesDTO? = dbQuery {
        val retro = Retrospectives.selectAll()
            .where { Retrospectives.id eq id }
            .singleOrNull()
            ?.toRetrospectiveDTO() ?: return@dbQuery null

        val notes = (RetrospectiveNotes innerJoin Notes)
            .selectAll()
            .where { RetrospectiveNotes.retrospectiveId eq id }
            .map { it.toNoteDTO() }

        RetrospectiveWithNotesDTO(
            id = retro.id,
            scheduledDate = retro.scheduledDate,
            startedAt = retro.startedAt,
            completedAt = retro.completedAt,
            status = retro.status,
            aiDiscussionPoints = retro.aiDiscussionPoints,
            finalSummary = retro.finalSummary,
            notes = notes,
            createdAt = retro.createdAt
        )
    }

    override suspend fun findAll(): List<RetrospectiveDTO> = dbQuery {
        Retrospectives.selectAll()
            .orderBy(Retrospectives.createdAt to SortOrder.DESC)
            .map { it.toRetrospectiveDTO() }
    }

    override suspend fun findByUser(userId: UUID): List<RetrospectiveDTO> = dbQuery {
        // Find retrospectives where user is a participant
        val retroIds = RetrospectiveUsers
            .selectAll()
            .where { RetrospectiveUsers.userId eq userId }
            .map { it[RetrospectiveUsers.retrospectiveId] }

        if (retroIds.isEmpty()) {
            emptyList()
        } else {
            Retrospectives.selectAll()
                .where { Retrospectives.id inList retroIds }
                .orderBy(Retrospectives.createdAt to SortOrder.DESC)
                .map { it.toRetrospectiveDTO() }
        }
    }

    override suspend fun userHasAccessToRetro(retroId: UUID, userId: UUID): Boolean = dbQuery {
        // Check if user is a participant in this retrospective
        RetrospectiveUsers.selectAll()
            .where {
                (RetrospectiveUsers.retrospectiveId eq retroId) and
                (RetrospectiveUsers.userId eq userId)
            }
            .count() > 0
    }

    override suspend fun addUser(retroId: UUID, userId: UUID): Boolean = dbQuery {
        RetrospectiveUsers.insert {
            it[retrospectiveId] = retroId
            it[RetrospectiveUsers.userId] = userId
        } != null
    }

    override suspend fun addNote(retroId: UUID, noteId: UUID): Boolean = dbQuery {
        RetrospectiveNotes.insert {
            it[retrospectiveId] = retroId
            it[RetrospectiveNotes.noteId] = noteId
        } != null
    }

    override suspend fun setDiscussionPoints(retroId: UUID, discussionPoints: String): Boolean = dbQuery {
        Retrospectives.update({ Retrospectives.id eq retroId }) {
            it[aiDiscussionPoints] = discussionPoints
        } > 0
    }

    override suspend fun complete(retroId: UUID, summary: String): Boolean = dbQuery {
        Retrospectives.update({ Retrospectives.id eq retroId }) {
            it[finalSummary] = summary
            it[completedAt] = CurrentDateTime
            it[status] = RetroStatus.COMPLETED
        } > 0
    }

    override suspend fun cancel(retroId: UUID): Boolean = dbQuery {
        Retrospectives.update({ Retrospectives.id eq retroId }) {
            it[status] = RetroStatus.CANCELLED
        } > 0
    }
}

private fun ResultRow.toRetrospectiveDTO() = RetrospectiveDTO(
    id = this[Retrospectives.id].value.toString(),
    scheduledDate = this[Retrospectives.scheduledDate]?.toString(),
    startedAt = this[Retrospectives.startedAt].toString(),
    completedAt = this[Retrospectives.completedAt]?.toString(),
    status = this[Retrospectives.status].name.lowercase(),
    aiDiscussionPoints = this[Retrospectives.aiDiscussionPoints],
    finalSummary = this[Retrospectives.finalSummary],
    createdAt = this[Retrospectives.createdAt].toString()
)

private fun ResultRow.toNoteDTO() = NoteDTO(
    id = this[Notes.id].value.toString(),
    userId = this[Notes.userId].value.toString(),
    content = this[Notes.content],
    status = this[Notes.status].name.lowercase(),
    mood = this[Notes.mood]?.name?.lowercase(),
    createdAt = this[Notes.createdAt].toString()
)
