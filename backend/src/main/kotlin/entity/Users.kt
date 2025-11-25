package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 100)
    val age = integer("age").nullable() // User's age for AI context
    val gender = varchar("gender", 50).nullable() // User's gender identity for AI context
    val description = text("description").nullable() // Self-description for AI context
    val preferredLanguage = varchar("preferred_language", 10).nullable() // e.g., "en", "es", "fr"
    val notificationToken = varchar("notification_token", 500).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}