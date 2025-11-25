package me.pavekovt.controller

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.pavekovt.dto.UpdateUserProfileRequest
import me.pavekovt.dto.UserDTO
import me.pavekovt.utils.getCurrentUserId
import me.pavekovt.service.UserService
import org.koin.ktor.ext.inject

fun Route.userRouting() {
    val userService by inject<UserService>()

    authenticate("jwt") {
        route("/users") {
            /**
             * Update user profile (name, age, gender, description, preferred language)
             */
            patch("/profile") {
                val userId = call.getCurrentUserId()
                val request = call.receive<UpdateUserProfileRequest>()

                val updatedUser = userService.updateProfile(
                    userId = userId,
                    name = request.name,
                    age = request.age,
                    gender = request.gender,
                    description = request.description,
                )

                call.respond<UserDTO>(updatedUser)
            }

            /**
             * Get current user profile
             */
            get("/profile") {
                val userId = call.getCurrentUserId()
                val user = userService.findById(userId)
                    ?: throw IllegalStateException("User not found")
                call.respond<UserDTO>(user)
            }
        }
    }
}
