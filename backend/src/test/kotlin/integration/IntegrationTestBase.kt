package me.pavekovt.integration

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.pavekovt.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * Base class for integration tests that sets up:
 * - Postgres testcontainer
 * - Ktor test server
 * - HTTP client for making requests
 */
abstract class IntegrationTestBase {

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
            }

        init {
            postgres.portBindings = listOf("9999:5432")
            postgres.start()
        }
    }

    protected lateinit var client: HttpClient
    protected lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    protected var baseUrl: String = ""

    @BeforeTest
    fun setup() {
        // Set database connection properties for the test
        System.setProperty("DB_URL", postgres.jdbcUrl)
        System.setProperty("DB_USER", postgres.username)
        System.setProperty("DB_PASSWORD", postgres.password)

        // Set JWT configuration for tests
        System.setProperty("JWT_SECRET", "test-secret-key-for-jwt-that-is-long-enough-for-testing")
        System.setProperty("JWT_AUDIENCE", "test-audience")
        System.setProperty("JWT_ISSUER", "test-issuer")
        System.setProperty("JWT_REALM", "test-realm")

        // Start Ktor server (non-blocking)
        server = embeddedServer(Netty, port = 0) {
            module()
        }.start(wait = false)

        // Get port after server starts
        runBlocking {
            val port = server.engine.resolvedConnectors().first().port
            baseUrl = "http://localhost:$port"
        }

        // Create HTTP client
        client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
        }
    }

    @AfterTest
    fun teardown() = runBlocking {
        if (::client.isInitialized) {
            client.close()
        }
        if (::server.isInitialized) {
            server.stop(1000, 2000)
        }
    }
}
