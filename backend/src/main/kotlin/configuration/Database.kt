package me.pavekovt.configuration

import io.ktor.server.application.*
import me.pavekovt.entity.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    Database.connect(
        url = "jdbc:postgresql://localhost:5432/conflict_manager",
        driver = "org.postgresql.Driver",
        user = "dev_user",
        password = "dev_password"
    )

    transaction {
        SchemaUtils.create(
            Users,
            Notes,
            Conflicts,
            Resolutions,
            AISummaries,
            Decisions,
            Retrospectives,
            RetrospectiveNotes,
            RetrospectiveUsers
        )
    }
}