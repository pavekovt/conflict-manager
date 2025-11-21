package me.pavekovt.repository

import me.pavekovt.dto.NoteDTO
import me.pavekovt.entity.Mood
import me.pavekovt.entity.NoteStatus
import java.util.UUID

interface NoteRepository {
    suspend fun create(userId: UUID, content: String, mood: Mood?): NoteDTO
    suspend fun findById(noteId: UUID): NoteDTO?
    suspend fun findByUser(userId: UUID, status: NoteStatus? = null): List<NoteDTO>
    suspend fun update(noteId: UUID, userId: UUID, content: String?, status: NoteStatus?, mood: Mood?): NoteDTO?
    suspend fun delete(noteId: UUID, userId: UUID): Boolean
    suspend fun findByRetro(retroId: UUID): List<NoteDTO>
}
