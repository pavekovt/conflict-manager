package me.pavekovt.service

import me.pavekovt.dto.NoteDTO
import me.pavekovt.entity.Mood
import me.pavekovt.entity.NoteStatus
import me.pavekovt.repository.NoteRepository
import java.util.UUID

class NoteService(
    private val noteRepository: NoteRepository
) {

    suspend fun create(userId: UUID, content: String, mood: String?): NoteDTO {
        require(content.isNotBlank()) { "Note content cannot be blank" }

        val moodEnum = mood?.let {
            runCatching { Mood.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid mood: $mood") }
        }

        return noteRepository.create(userId, content, moodEnum)
    }

    suspend fun findById(noteId: UUID, requestingUserId: UUID): NoteDTO? {
        val note = noteRepository.findById(noteId) ?: return null

        // Privacy: Only return if note belongs to requesting user
        return if (note.userId == requestingUserId.toString()) note else null
    }

    suspend fun findByUser(userId: UUID, status: String?): List<NoteDTO> {
        val statusEnum = status?.let {
            runCatching { NoteStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $status") }
        }

        return noteRepository.findByUser(userId, statusEnum)
    }

    suspend fun update(
        noteId: UUID,
        userId: UUID,
        content: String?,
        status: String?,
        mood: String?
    ): NoteDTO {
        content?.let {
            require(it.isNotBlank()) { "Note content cannot be blank" }
        }

        val statusEnum = status?.let {
            runCatching { NoteStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid status: $status") }
        }

        val moodEnum = mood?.let {
            runCatching { Mood.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid mood: $mood") }
        }

        return noteRepository.update(noteId, userId, content, statusEnum, moodEnum)
            ?: throw IllegalStateException("Note not found or you don't have permission")
    }

    suspend fun delete(noteId: UUID, userId: UUID): Boolean {
        return noteRepository.delete(noteId, userId)
    }
}
