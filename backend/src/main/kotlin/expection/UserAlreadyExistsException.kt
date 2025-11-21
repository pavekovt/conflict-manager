package me.pavekovt.expection

class UserAlreadyExistsException(email: String): Exception("User with email exists: $email")