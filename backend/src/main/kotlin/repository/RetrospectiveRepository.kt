package me.pavekovt.repository

import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.entity.RetroStatus
import java.util.UUID
import kotlinx.datetime.LocalDateTime

interface RetrospectiveRepository {
    suspend fun create(scheduledDate: LocalDateTime?, status: RetroStatus, userIds: List<UUID>): RetrospectiveDTO
    suspend fun findById(id: UUID): RetrospectiveDTO?
    suspend fun findByIdWithNotes(id: UUID): RetrospectiveWithNotesDTO?
    suspend fun findAll(): List<RetrospectiveDTO>
    suspend fun findByUser(userId: UUID): List<RetrospectiveDTO>
    suspend fun userHasAccessToRetro(retroId: UUID, userId: UUID): Boolean
    suspend fun addUser(retroId: UUID, userId: UUID): Boolean
    suspend fun addNote(retroId: UUID, noteId: UUID): Boolean
    suspend fun getNotesForRetrospective(retroId: UUID): List<NoteDTO>
    suspend fun setDiscussionPoints(retroId: UUID, discussionPoints: String): Boolean
    suspend fun updateDiscussionPoints(retroId: UUID, discussionPoints: String): Boolean
    suspend fun updateStatus(retroId: UUID, status: RetroStatus): Boolean
    suspend fun complete(retroId: UUID, summary: String): Boolean
    suspend fun cancel(retroId: UUID): Boolean
}
