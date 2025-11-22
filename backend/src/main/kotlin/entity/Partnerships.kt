package me.pavekovt.entity

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object Partnerships : UUIDTable("partnerships") {
    val userId1 = reference("user_id_1", Users)
    val userId2 = reference("user_id_2", Users)
    val status = varchar("status", 20) // pending, active, ended
    val initiatedBy = reference("initiated_by", Users)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val acceptedAt = datetime("accepted_at").nullable()
    val endedAt = datetime("ended_at").nullable()
}
