package me.pavekovt.facade

import me.pavekovt.dto.NoteDTO
import me.pavekovt.dto.exchange.CreateNoteRequest
import me.pavekovt.dto.exchange.UpdateNoteRequest
import me.pavekovt.service.NoteService
import java.util.UUID

/**
 * Facade for note operations.
 * Enforces user ownership validation for notes.
 */
class NoteFacade(
    private val noteService: NoteService
) {

    suspend fun create(request: CreateNoteRequest, userId: UUID): NoteDTO {
        return noteService.create(
            userId = userId,
            content = request.content,
            mood = request.mood
        )
    }

    suspend fun findAll(status: String?, userId: UUID): List<NoteDTO> {
        return noteService.findByUser(userId, status)
    }

    suspend fun findById(noteId: UUID, userId: UUID): NoteDTO {
        return noteService.findById(noteId, userId)
            ?: throw IllegalStateException("Note not found or you don't have permission")
    }

    suspend fun update(noteId: UUID, request: UpdateNoteRequest, userId: UUID): NoteDTO {
        return noteService.update(
            noteId = noteId,
            userId = userId,
            content = request.content,
            status = request.status,
            mood = request.mood
        )
    }

    suspend fun delete(noteId: UUID, userId: UUID) {
        noteService.delete(noteId, userId)
    }
}
