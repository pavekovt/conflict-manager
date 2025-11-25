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

/**
 * Simple service for retrospective data operations.
 * Business logic and validation should be in RetrospectiveFacade.
 */
class RetrospectiveService(
    private val retrospectiveRepository: RetrospectiveRepository,
    private val noteRepository: NoteRepository,
    private val aiProvider: AIProvider
) {

    suspend fun create(scheduledDate: String?, userIds: List<UUID>): RetrospectiveDTO {
        val scheduledDateTime = scheduledDate?.let { LocalDateTime.parse(it) }

        val status = if (scheduledDateTime != null) {
            RetroStatus.SCHEDULED
        } else {
            RetroStatus.IN_PROGRESS
        }

        return retrospectiveRepository.create(scheduledDateTime, status, userIds)
    }

    suspend fun findByUser(userId: UUID): List<RetrospectiveDTO> {
        return retrospectiveRepository.findByUser(userId)
    }

    suspend fun findById(id: UUID): RetrospectiveDTO {
        return retrospectiveRepository.findById(id)
            ?: throw IllegalStateException("Retrospective not found")
    }

    suspend fun findByIdWithNotes(id: UUID): RetrospectiveWithNotesDTO {
        return retrospectiveRepository.findByIdWithNotes(id)
            ?: throw IllegalStateException("Retrospective not found")
    }

    suspend fun userHasAccess(retroId: UUID, userId: UUID): Boolean {
        return retrospectiveRepository.userHasAccessToRetro(retroId, userId)
    }

    suspend fun addNote(retroId: UUID, noteId: UUID) {
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

        // Update status to PENDING_APPROVAL
        retrospectiveRepository.updateStatus(retroId, RetroStatus.PENDING_APPROVAL)
    }

    suspend fun approve(retroId: UUID, userId: UUID, approvalText: String) {
        require(approvalText.isNotBlank()) { "Approval text cannot be blank" }

        // Approve by this user with their explanation
        retrospectiveRepository.approve(retroId, userId, approvalText)
    }

    suspend fun isApprovedByBoth(retroId: UUID): Boolean {
        return retrospectiveRepository.isApprovedByBoth(retroId)
    }

    suspend fun complete(retroId: UUID, finalSummary: String) {
        // Verify both users approved before allowing completion
        if (!retrospectiveRepository.isApprovedByBoth(retroId)) {
            throw IllegalStateException("Cannot complete retrospective - both users must approve first")
        }

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
