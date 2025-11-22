package me.pavekovt.configuration

import io.ktor.server.application.*
import io.ktor.server.config.*
import me.pavekovt.ai.*
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
                        ?: throw IllegalStateException("JWT secret not configured"),
                    audience = System.getProperty("JWT_AUDIENCE")
                        ?: config.propertyOrNull("jwt.audience")?.getString()
                        ?: throw IllegalStateException("JWT audience not configured"),
                    realm = System.getProperty("JWT_REALM")
                        ?: config.propertyOrNull("jwt.realm")?.getString()
                        ?: throw IllegalStateException("JWT realm not configured"),
                    issuer = System.getProperty("JWT_ISSUER")
                        ?: config.propertyOrNull("jwt.issuer")?.getString()
                        ?: throw IllegalStateException("JWT issuer not configured")
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
             * Services
             */
            single { AuthService(get(), get()) }
            single { NoteService(get()) }
            single { PartnershipService(get(), get()) }
            single { ConflictService(get(), get(), get(), get(), get(), get()) }
            single { DecisionService(get(), get()) }
            single { RetrospectiveService(get(), get(), get(), get()) }
        })
    }
}
