package me.pavekovt.exception

class UserAlreadyExistsException(email: String) : Exception("User with email already exists: $email")
