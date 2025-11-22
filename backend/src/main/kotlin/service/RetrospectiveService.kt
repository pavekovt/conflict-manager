package me.pavekovt.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.entity.NoteStatus
import me.pavekovt.entity.RetroStatus
import me.pavekovt.repository.NoteRepository
import me.pavekovt.repository.RetrospectiveRepository
import java.util.UUID
import kotlinx.datetime.LocalDateTime

class RetrospectiveService(
    private val retrospectiveRepository: RetrospectiveRepository,
    private val noteRepository: NoteRepository,
    private val aiProvider: AIProvider,
    private val partnershipService: PartnershipService
) {

    suspend fun create(scheduledDate: String?, currentUserId: UUID): RetrospectiveDTO {
        val scheduledDateTime = scheduledDate?.let { LocalDateTime.parse(it) }

        // Verify user has an active partnership
        val partnerId = partnershipService.requirePartnership(currentUserId)

        val status = if (scheduledDateTime != null) {
            RetroStatus.SCHEDULED
        } else {
            RetroStatus.IN_PROGRESS
        }

        // Create retrospective for both partners
        val userIds = listOf(currentUserId, partnerId)
        return retrospectiveRepository.create(scheduledDateTime, status, userIds)
    }

    suspend fun findAll(currentUserId: UUID): List<RetrospectiveDTO> {
        // Only show retrospectives for this user's partnership
        return retrospectiveRepository.findByUser(currentUserId)
    }

    suspend fun findByUser(userId: UUID): List<RetrospectiveDTO> {
        return retrospectiveRepository.findByUser(userId)
    }

    suspend fun findById(id: UUID, userId: UUID): RetrospectiveDTO {
        val retro = retrospectiveRepository.findById(id)
            ?: throw IllegalStateException("Retrospective not found")

        // Check if user has access
        if (!retrospectiveRepository.userHasAccessToRetro(id, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        return retro
    }

    suspend fun findByIdWithNotes(id: UUID, userId: UUID): RetrospectiveWithNotesDTO {
        val retro = retrospectiveRepository.findByIdWithNotes(id)
            ?: throw IllegalStateException("Retrospective not found")

        // Check if user has access
        if (!retrospectiveRepository.userHasAccessToRetro(id, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        return retro
    }

    suspend fun addNote(retroId: UUID, noteId: UUID, userId: UUID) {
        // Verify note belongs to user
        val note = noteRepository.findById(noteId)
            ?: throw IllegalStateException("Note not found")

        if (note.userId != userId.toString()) {
            throw IllegalStateException("You can only add your own notes to retrospective")
        }

        // Verify note is ready for discussion
        if (note.status != NoteStatus.READY_FOR_DISCUSSION.name.lowercase()) {
            throw IllegalStateException("Note must be marked as 'ready_for_discussion' before adding to retrospective")
        }

        retrospectiveRepository.addNote(retroId, noteId)
    }

    suspend fun generateDiscussionPoints(retroId: UUID) {
        val retro = retrospectiveRepository.findByIdWithNotes(retroId)
            ?: throw IllegalStateException("Retrospective not found")

        if (retro.notes.isEmpty()) {
            throw IllegalStateException("Cannot generate discussion points for retrospective with no notes")
        }

        // Generate AI discussion points
        val result = aiProvider.generateRetroPoints(retro.notes)

        // Convert to JSON string for storage
        val discussionPointsJson = Json.encodeToString(result.discussionPoints)

        retrospectiveRepository.setDiscussionPoints(retroId, discussionPointsJson)
    }

    suspend fun complete(retroId: UUID, finalSummary: String) {
        require(finalSummary.isNotBlank()) { "Final summary cannot be blank" }

        val success = retrospectiveRepository.complete(retroId, finalSummary)
        if (!success) {
            throw IllegalStateException("Retrospective not found")
        }

        // Mark all notes in this retro as discussed
        val retro = retrospectiveRepository.findByIdWithNotes(retroId)
            ?: throw IllegalStateException("Retrospective not found")

        retro.notes.forEach { note ->
            noteRepository.update(
                noteId = UUID.fromString(note.id),
                userId = UUID.fromString(note.userId),
                content = null,
                status = NoteStatus.DISCUSSED,
                mood = null
            )
        }
    }

    suspend fun cancel(retroId: UUID) {
        val success = retrospectiveRepository.cancel(retroId)
        if (!success) {
            throw IllegalStateException("Retrospective not found")
        }
    }
}
