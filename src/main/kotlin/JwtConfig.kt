package com.provingground

import com.provingground.datamodels.User

object JwtConfig {
    private const val secret = "replace-this-with-a-long-random-secret"
    const val issuer = "soccer-club-app"
    const val audience = "soccer-club-app-users"
    private const val validityInMs = 1000L * 60L * 60L * 24L * 7L // 7 days

    private val algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)

    fun generateToken(user: User): String {
        val now = System.currentTimeMillis()

        return com.auth0.jwt.JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id.toString())
            .withClaim("username", user.username)
            .withClaim("role", user.role.name)
            .withExpiresAt(java.util.Date(now + validityInMs))
            .sign(algorithm)
    }

    fun verifier(): com.auth0.jwt.JWTVerifier {
        return com.auth0.jwt.JWT
            .require(algorithm)
            .withAudience(audience)
            .withIssuer(issuer)
            .build()
    }
}