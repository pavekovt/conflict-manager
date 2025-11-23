package me.pavekovt.configuration

import io.ktor.server.application.*
import io.ktor.server.config.*
import me.pavekovt.ai.*
import me.pavekovt.facade.*
import me.pavekovt.properties.AuthenticationProperties
import me.pavekovt.repository.*
import me.pavekovt.service.*
import org.koin.core.module.dsl.singleOf
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

            /**
             * AI Provider
             */
            single<AIProvider> { MockAIProvider() }

            /**
             * Repositories
             */
            single<UserRepository> { UserRepositoryImpl() }
            single<NoteRepository> { NoteRepositoryImpl() }
            single<ConflictRepository> { ConflictRepositoryImpl() }
            single<ResolutionRepository> { ResolutionRepositoryImpl() }
            single<AISummaryRepository> { AISummaryRepositoryImpl() }
            single<DecisionRepository> { DecisionRepositoryImpl() }
            single<RetrospectiveRepository> { RetrospectiveRepositoryImpl() }
            single<PartnershipRepository> { PartnershipRepositoryImpl() }

            /**
             * Services (simplified - no complex business logic)
             */
            single { AuthService(get(), get()) }
            single { UserService(get()) }
            single { NoteService(get()) }
            single { PartnershipService(get()) }
            single { ConflictService(get(), get(), get(), get(), get()) }
            single { DecisionService(get()) }
            single { RetrospectiveService(get(), get(), get()) }

            /**
             * Ownership Validation
             */
            single<OwnershipValidator> { PartnershipOwnershipValidator(get()) }

            /**
             * Facades (orchestrate services and handle business logic)
             */
            single { PartnershipFacade(get(), get()) }
            single { NoteFacade(get()) }
            single { ConflictFacade(get(), get()) }
            single { DecisionFacade(get(), get()) }
            single { RetrospectiveFacade(get(), get(), get()) }
        })
    }
}
