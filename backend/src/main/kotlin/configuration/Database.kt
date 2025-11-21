package me.pavekovt.configuration

import io.ktor.server.application.*
import me.pavekovt.entity.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    val db = Database.connect(
        url = "jdbc:postgresql://localhost:5432/conflict_manager",
        user = "dev_user",
        driver = "org.postgresql.Driver",
        password = "dev_password",
    )

    transaction {
        SchemaUtils.create(Users)
    }
}