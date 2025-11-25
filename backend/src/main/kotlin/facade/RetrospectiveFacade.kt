package me.pavekovt.facade

import me.pavekovt.ai.AIProvider
import me.pavekovt.dto.RetrospectiveDTO
import me.pavekovt.dto.RetrospectiveWithNotesDTO
import me.pavekovt.entity.NoteStatus
import me.pavekovt.repository.PartnershipContextRepository
import me.pavekovt.repository.PartnershipRepository
import me.pavekovt.service.NoteService
import me.pavekovt.service.RetrospectiveService
import java.util.UUID

/**
 * Facade for retrospective operations.
 * Orchestrates retrospective workflow and enforces partnership validation.
 * Updates partnership context after retrospective completion.
 */
class RetrospectiveFacade(
    private val retrospectiveService: RetrospectiveService,
    private val noteService: NoteService,
    private val ownershipValidator: OwnershipValidator,
    private val partnershipRepository: PartnershipRepository,
    private val partnershipContextRepository: PartnershipContextRepository,
    private val aiProvider: AIProvider
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

    suspend fun approve(retroId: UUID, userId: UUID, approvalText: String) {
        // Verify user has access
        if (!retrospectiveService.userHasAccess(retroId, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        retrospectiveService.approve(retroId, userId, approvalText)
    }

    suspend fun complete(retroId: UUID, finalSummary: String, userId: UUID) {
        require(finalSummary.isNotBlank()) { "Final summary cannot be blank" }

        // Verify user has access
        if (!retrospectiveService.userHasAccess(retroId, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        // Get retrospective with notes for context update
        val retroWithNotes = retrospectiveService.findByIdWithNotes(retroId)

        // Complete the retrospective
        retrospectiveService.complete(retroId, finalSummary)

        // Update partnership context
        val partnershipDTO = partnershipRepository.findActivePartnership(userId)
        if (partnershipDTO != null) {
            val partnershipId = UUID.fromString(partnershipDTO.id)
            val existingContext = partnershipContextRepository.getContext(partnershipId)?.compactedSummary

            // Extract note contents for context
            val noteContents = retroWithNotes.notes.map { it.content }

            val updatedContext = aiProvider.updatePartnershipContextWithRetrospective(
                existingContext = existingContext,
                retroSummary = finalSummary,
                retroNotes = noteContents,
                approvalText1 = retroWithNotes.approvalText1,
                approvalText2 = retroWithNotes.approvalText2
            )

            partnershipContextRepository.upsertContext(
                partnershipId = partnershipId,
                compactedSummary = updatedContext,
                incrementRetroCount = true
            )
        }
    }

    suspend fun cancel(retroId: UUID, userId: UUID) {
        // Verify user has access
        if (!retrospectiveService.userHasAccess(retroId, userId)) {
            throw IllegalStateException("You don't have access to this retrospective")
        }

        retrospectiveService.cancel(retroId)
    }
}
