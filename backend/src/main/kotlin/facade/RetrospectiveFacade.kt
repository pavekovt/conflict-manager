package me.pavekovt.facade

import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.entity.NoteStatus
import me.pavekovt.service.NoteService
import me.pavekovt.service.RetrospectiveService
import java.util.UUID

/**
 * Facade for retrospective operations.
 * Orchestrates retrospective workflow and enforces partnership validation.
 */
class RetrospectiveFacade(
    private val retrospectiveService: RetrospectiveService,
    private val noteService: NoteService,
    private val ownershipValidator: OwnershipValidator
) {

    suspend fun create(scheduledDate: String?, currentUserId: UUID): RetrospectiveDTO {
        // Verify user has an active partnership
        val partnerId = ownershipValidator.requirePartnership(currentUserId)

        // Create retrospective for both partners
        val userIds = listOf(currentUserId, partnerId)
        return retrospectiveService.create(scheduledDate, userIds)
    }

    suspend fun findAll(currentUserId: UUID): List<RetrospectiveDTO> {
        // Only show retrospectives for this user
        return retrospectiveService.findByUser(currentUserId)
    }

    suspend fun findById(id: UUID, userId: UUID): RetrospectiveDTO {
        val retro = retrospectiveService.findById(id)

        // Check if user has access
        if (!retrospectiveService.userHasAccess(id, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        return retro
    }

    suspend fun findByIdWithNotes(id: UUID, userId: UUID): RetrospectiveWithNotesDTO {
        val retro = retrospectiveService.findByIdWithNotes(id)

        // Check if user has access
        if (!retrospectiveService.userHasAccess(id, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        return retro
    }

    suspend fun addNote(retroId: UUID, noteId: UUID, userId: UUID) {
        // Verify note belongs to user
        val note = noteService.findById(noteId, userId)
            ?: throw IllegalStateException("Note not found or you don't have permission")

        // Verify note is ready for discussion
        if (note.status != NoteStatus.READY_FOR_DISCUSSION.name.lowercase()) {
            throw IllegalStateException("Note must be marked as 'ready_for_discussion' before adding to retrospective")
        }

        retrospectiveService.addNote(retroId, noteId)
    }

    suspend fun generateDiscussionPoints(retroId: UUID, userId: UUID) {
        // Verify user has access
        if (!retrospectiveService.userHasAccess(retroId, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        retrospectiveService.generateDiscussionPoints(retroId)
    }

    suspend fun complete(retroId: UUID, finalSummary: String, userId: UUID) {
        require(finalSummary.isNotBlank()) { "Final summary cannot be blank" }

        // Verify user has access
        if (!retrospectiveService.userHasAccess(retroId, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        retrospectiveService.complete(retroId, finalSummary)
    }

    suspend fun cancel(retroId: UUID, userId: UUID) {
        // Verify user has access
        if (!retrospectiveService.userHasAccess(retroId, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        retrospectiveService.cancel(retroId)
    }
}
