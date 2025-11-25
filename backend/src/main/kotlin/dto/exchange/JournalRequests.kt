package me.pavekovt.dto.exchange

import kotlinx.serialization.Serializable

@Serializable
data class CreateJournalEntryRequest(
    val content: String
)

@Serializable
data class UpdateJournalEntryRequest(
    val content: String
)
