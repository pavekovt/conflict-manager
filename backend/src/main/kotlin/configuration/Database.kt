package me.pavekovt.configuration

import io.ktor.server.application.*
import me.pavekovt.entity.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    // Read database config from system properties (for tests) or environment
    val dbUrl = System.getProperty("DB_URL")
        ?: environment.config.propertyOrNull("postgres.url")?.getString()
        ?: "jdbc:postgresql://localhost:5432/conflict_manager"

    val dbUser = System.getProperty("DB_USER")
        ?: environment.config.propertyOrNull("postgres.user")?.getString()
        ?: "dev_user"

    val dbPassword = System.getProperty("DB_PASSWORD")
        ?: environment.config.propertyOrNull("postgres.password")?.getString()
        ?: "dev_password"

    Database.connect(
        url = dbUrl,
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword
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
            Partnerships,
            RetrospectiveNotes,
            RetrospectiveUsers
        )
    }
}