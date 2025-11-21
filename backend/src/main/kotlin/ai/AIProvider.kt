package me.pavekovt.ai

import me.pavekovt.dto.NoteDTO

interface AIProvider {
    suspend fun summarizeConflict(resolution1: String, resolution2: String): SummaryResult
    suspend fun generateRetroPoints(notes: List<NoteDTO>): RetroPointsResult
}

data class SummaryResult(
    val summary: String,
    val provider: String,
    val discrepancies: List<String>? = null
)

data class RetroPointsResult(
    val discussionPoints: List<DiscussionPoint>,
    val provider: String
)

data class DiscussionPoint(
    val theme: String,
    val relatedNoteIds: List<String>,
    val suggestedApproach: String
)
