package me.pavekovt.dto.exchange

data class CreateDecisionRequest(
    val summary: String,
    val category: String?
)

data class UpdateDecisionStatusRequest(
    val status: String
)
