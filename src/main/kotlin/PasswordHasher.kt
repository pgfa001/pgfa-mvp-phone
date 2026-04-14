package com.provingground

import org.mindrot.jbcrypt.BCrypt

class PasswordHasher(
    private val logRounds: Int = 12
) {
    fun hash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(logRounds))
    }

    fun verify(password: String, passwordHash: String): Boolean {
        return try {
            BCrypt.checkpw(password, passwordHash)
        } catch (_: Exception) {
            false
        }
    }
}