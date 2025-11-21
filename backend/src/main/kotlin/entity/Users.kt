package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 100)
    val notificationToken = varchar("notification_token", 500).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}