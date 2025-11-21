package me.pavekovt.configuration

import io.ktor.server.application.*
import io.ktor.server.config.property
import me.pavekovt.properties.AuthenticationProperties
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import repository.UserRepository
import repository.UserRepositoryImpl
import service.AuthService

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
             * Repositories
             */
            singleOf<UserRepository>(::UserRepositoryImpl)

            /**
             * Services
             */
            singleOf(::AuthService)
        })
    }
}
