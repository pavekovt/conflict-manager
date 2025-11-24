package me.pavekovt.properties

import kotlinx.serialization.Serializable

@Serializable
data class AIProperties(
    val provider: String, // "mock" or "claude"
    val apiKey: String?,
    val model: String = "claude-3-5-sonnet-20241022"
)
