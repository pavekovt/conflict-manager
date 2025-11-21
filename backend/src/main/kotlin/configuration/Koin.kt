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
             * Properties
             */
            single<AuthenticationProperties> {
                property<AuthenticationProperties>("jwt")
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

            /**
             * Services
             */
            single { AuthService(get(), get()) }
            single { NoteService(get()) }
            single { ConflictService(get(), get(), get(), get(), get()) }
            single { DecisionService(get()) }
            single { RetrospectiveService(get(), get(), get()) }
        })
    }
}
