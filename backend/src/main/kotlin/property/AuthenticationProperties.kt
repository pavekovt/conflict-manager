package me.pavekovt.properties

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationProperties(
    val secret: String,
    val audience: String,
    val realm: String,
    val issuer: String
)
