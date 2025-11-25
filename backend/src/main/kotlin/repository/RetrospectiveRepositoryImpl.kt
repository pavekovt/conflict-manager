package me.pavekovt.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import me.pavekovt.ai.DiscussionPoint
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
            approvedByUserId1 = retro.approvedByUserId1,
            approvedByUserId2 = retro.approvedByUserId2,
            approvalText1 = retro.approvalText1,
            approvalText2 = retro.approvalText2,
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

    override suspend fun getNotesForRetrospective(retroId: UUID): List<NoteDTO> = dbQuery {
        (RetrospectiveNotes innerJoin Notes)
            .selectAll()
            .where { RetrospectiveNotes.retrospectiveId eq retroId }
            .map { it.toNoteDTO() }
    }

    override suspend fun getUsersForRetrospective(retroId: UUID): List<UUID> = dbQuery {
        RetrospectiveUsers.selectAll()
            .where { RetrospectiveUsers.retrospectiveId eq retroId }
            .map { it[RetrospectiveUsers.userId].value }
    }

    override suspend fun updateDiscussionPoints(retroId: UUID, discussionPoints: String): Boolean = dbQuery {
        Retrospectives.update({ Retrospectives.id eq retroId }) {
            it[aiDiscussionPoints] = discussionPoints
        } > 0
    }

    override suspend fun updateStatus(retroId: UUID, status: RetroStatus): Boolean = dbQuery {
        Retrospectives.update({ Retrospectives.id eq retroId }) {
            it[Retrospectives.status] = status
        } > 0
    }

    override suspend fun approve(retroId: UUID, userId: UUID, approvalText: String): Boolean = dbQuery {
        val retro = Retrospectives.selectAll()
            .where { Retrospectives.id eq retroId }
            .singleOrNull() ?: return@dbQuery false

        val currentApprover1 = retro[Retrospectives.approvedByUserId1]
        val currentApprover2 = retro[Retrospectives.approvedByUserId2]

        // Check if user already approved
        if (currentApprover1 == userId || currentApprover2 == userId) {
            return@dbQuery false // Already approved
        }

        // Add userId and approval text to first available slot
        Retrospectives.update({ Retrospectives.id eq retroId }) {
            when {
                currentApprover1 == null -> {
                    it[approvedByUserId1] = userId
                    it[Retrospectives.approvalText1] = approvalText
                }
                currentApprover2 == null -> {
                    it[approvedByUserId2] = userId
                    it[Retrospectives.approvalText2] = approvalText
                }
                else -> return@update // Both slots full (shouldn't happen in 2-person retro)
            }
        }
        true
    }

    override suspend fun isApprovedByBoth(retroId: UUID): Boolean = dbQuery {
        Retrospectives.selectAll()
            .where { Retrospectives.id eq retroId }
            .singleOrNull()
            ?.let { row ->
                row[Retrospectives.approvedByUserId1] != null &&
                row[Retrospectives.approvedByUserId2] != null
            } ?: false
    }
}

private fun ResultRow.toRetrospectiveDTO() = RetrospectiveDTO(
    id = this[Retrospectives.id].value.toString(),
    scheduledDate = this[Retrospectives.scheduledDate]?.toString(),
    startedAt = this[Retrospectives.startedAt].toString(),
    completedAt = this[Retrospectives.completedAt]?.toString(),
    status = this[Retrospectives.status].name.lowercase(),
    aiDiscussionPoints = this[Retrospectives.aiDiscussionPoints]?.let { json ->
        try {
            Json.decodeFromString<List<DiscussionPoint>>(json)
        } catch (e: Exception) {
            null
        }
    },
    finalSummary = this[Retrospectives.finalSummary],
    approvedByUserId1 = this[Retrospectives.approvedByUserId1]?.toString(),
    approvedByUserId2 = this[Retrospectives.approvedByUserId2]?.toString(),
    approvalText1 = this[Retrospectives.approvalText1],
    approvalText2 = this[Retrospectives.approvalText2],
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
