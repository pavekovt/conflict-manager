package me.pavekovt.repository

import me.pavekovt.db.dbQuery
import me.pavekovt.dto.NoteDTO
import me.pavekovt.entity.*
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import java.util.UUID

class NoteRepositoryImpl : NoteRepository {

    override suspend fun create(userId: UUID, content: String, mood: Mood?): NoteDTO = dbQuery {
        Notes.insertReturning {
            it[Notes.userId] = userId
            it[Notes.content] = content
            it[Notes.mood] = mood
        }
            .single()
            .toNoteDTO()
    }

    override suspend fun findById(noteId: UUID): NoteDTO? = dbQuery {
        Notes.selectAll()
            .where { Notes.id eq noteId }
            .singleOrNull()
            ?.toNoteDTO()
    }

    override suspend fun findByUser(userId: UUID, status: NoteStatus?): List<NoteDTO> = dbQuery {
        Notes.selectAll()
            .where { Notes.userId eq userId }
            .apply {
                if (status != null) {
                    andWhere { Notes.status eq status }
                }
            }
            .orderBy(Notes.createdAt to SortOrder.DESC)
            .map { it.toNoteDTO() }
    }

    override suspend fun update(
        noteId: UUID,
        userId: UUID,
        content: String?,
        status: NoteStatus?,
        mood: Mood?
    ): NoteDTO? = dbQuery {
        val updated = Notes.update({
            (Notes.id eq noteId) and (Notes.userId eq userId)
        }) {
            if (content != null) it[Notes.content] = content
            if (status != null) it[Notes.status] = status
            if (mood != null) it[Notes.mood] = mood
        }

        if (updated > 0) {
            Notes.selectAll()
                .where { Notes.id eq noteId }
                .singleOrNull()
                ?.toNoteDTO()
        } else null
    }

    override suspend fun delete(noteId: UUID, userId: UUID): Boolean = dbQuery {
        Notes.deleteWhere {
            (Notes.id eq noteId) and (Notes.userId eq userId)
        } > 0
    }

    override suspend fun findByRetro(retroId: UUID): List<NoteDTO> = dbQuery {
        (RetrospectiveNotes innerJoin Notes)
            .selectAll()
            .where { RetrospectiveNotes.retrospectiveId eq retroId }
            .map { it.toNoteDTO() }
    }
}

private fun ResultRow.toNoteDTO() = NoteDTO(
    id = this[Notes.id].value.toString(),
    userId = this[Notes.userId].value.toString(),
    content = this[Notes.content],
    status = this[Notes.status].name.lowercase(),
    mood = this[Notes.mood]?.name?.lowercase(),
    createdAt = this[Notes.createdAt].toString()
)
