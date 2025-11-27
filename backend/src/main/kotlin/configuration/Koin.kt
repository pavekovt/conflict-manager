package me.pavekovt.configuration

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import me.pavekovt.ai.AIProvider
import me.pavekovt.ai.ClaudeAIProvider
import me.pavekovt.ai.MockAIProvider
import me.pavekovt.facade.*
import me.pavekovt.properties.AIProperties
import me.pavekovt.properties.AuthenticationProperties
import me.pavekovt.repository.*
import me.pavekovt.service.*
import me.pavekovt.service.job.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        modules(module {
            /**
             * Application instance
             */
            single { this@configureFrameworks }

            /**
             * Properties
             */
            single<AuthenticationProperties> {
                val config = get<Application>().environment.config

                AuthenticationProperties(
                    secret = System.getProperty("JWT_SECRET")
                        ?: config.propertyOrNull("jwt.secret")?.getString()
                        ?: "test-secret-key-for-jwt-that-is-long-enough-for-testing",
                    audience = System.getProperty("JWT_AUDIENCE")
                        ?: config.propertyOrNull("jwt.audience")?.getString()
                        ?: "test-audience",
                    realm = System.getProperty("JWT_REALM")
                        ?: config.propertyOrNull("jwt.realm")?.getString()
                        ?: "test-realm",
                    issuer = System.getProperty("JWT_ISSUER")
                        ?: config.propertyOrNull("jwt.issuer")?.getString()
                        ?: "test-issuer"
                )
            }

            single<AIProperties> {
                val config = get<Application>().environment.config

                AIProperties(
                    provider = System.getenv("AI_PROVIDER")
                        ?: config.propertyOrNull("ai.provider")?.getString()
                        ?: "mock",
                    apiKey = System.getenv("CLAUDE_API_KEY")
                        ?: config.propertyOrNull("ai.apiKey")?.getString(),
                    model = System.getenv("AI_MODEL")
                        ?: config.propertyOrNull("ai.model")?.getString()
                        ?: "claude-3-5-sonnet-20241022"
                )
            }

            /**
             * HTTP Client for AI API calls
             */
            single<HttpClient> {
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json(Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                        })
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15000
                    }
                }
            }

            /**
             * AI Provider (switch between Mock and Claude based on config)
             */
            single<AIProvider> {
                val aiProperties = get<AIProperties>()
                when (aiProperties.provider.lowercase()) {
                    "claude" -> {
                        val apiKey = aiProperties.apiKey
                            ?: throw IllegalStateException("Claude API key not configured. Set CLAUDE_API_KEY environment variable or ai.apiKey in config.")
                        ClaudeAIProvider(
                            apiKey = apiKey,
                            httpClient = get(),
                            model = aiProperties.model
                        )
                    }

                    "mock" -> MockAIProvider()
                    else -> throw IllegalStateException("Unknown AI provider: ${aiProperties.provider}. Use 'mock' or 'claude'.")
                }
            }

            /**
             * Repositories
             */
            single<UserRepository> { UserRepositoryImpl() }
            single<NoteRepository> { NoteRepositoryImpl() }
            single<ConflictRepository> { ConflictRepositoryImpl() }
            single<ConflictFeelingsRepository> { ConflictFeelingsRepositoryImpl() }
            single<ResolutionRepository> { ResolutionRepositoryImpl() }
            single<AISummaryRepository> { AISummaryRepositoryImpl() }
            single<DecisionRepository> { DecisionRepositoryImpl() }
            single<RetrospectiveRepository> { RetrospectiveRepositoryImpl() }
            single<PartnershipRepository> { PartnershipRepositoryImpl() }
            single<PartnershipContextRepository> { PartnershipContextRepositoryImpl() }
            single<JobRepository> { JobRepositoryImpl() }
            single<JournalRepository> { JournalRepositoryImpl() }

            /**
             * Services (simplified - no complex business logic)
             */
            single { AuthService(get(), get()) }
            single { UserService(get()) }
            single { NoteService(get()) }
            single { PartnershipService(get()) }
            single { ConflictService(get(), get(), get(), get(), get(), get(), get()) }
            single { DecisionService(get()) }
            single { RetrospectiveService(get(), get(), get()) }
            single { JournalService(get()) }
            single { JournalContextProcessor(get(), get(), get(), get(), get()) }

            /**
             * Background Job Processing (async AI operations)
             */
            // Job helpers
            single { UserProfileLoader(get()) }
            single { PartnershipContextLoader(get(), get(), get()) }

            // Job handlers
            single { ProcessFeelingsJobHandler(get(), get(), get(), get(), get(), get(), get()) }
            single { GenerateSummaryJobHandler(get(), get(), get(), get(), get(), get()) }
            single { GenerateDiscussionPointsJobHandler(get(), get(), get()) }
            single { UpdatePartnershipContextJobHandler(get(), get(), get(), get(), get(), get(), get()) }

            // Main job processor
            single { JobProcessorService(get(), get(), get(), get(), get()) }


            /**
             * Ownership Validation
             */
            single<OwnershipValidator> { PartnershipOwnershipValidator(get()) }

            /**
             * Facades (orchestrate services and handle business logic)
             */
            single { PartnershipFacade(get(), get(), get(), get(), get(), get()) }
            single { NoteFacade(get()) }
            single { ConflictFacade(get(), get(), get(), get()) }
            single { DecisionFacade(get(), get()) }
            single { RetrospectiveFacade(get(), get(), get(), get(), get(), get()) }
            single { JournalFacade(get(), get(), get()) }
        })
    }
}
