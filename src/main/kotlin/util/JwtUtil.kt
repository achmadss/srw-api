package util

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.*

object JwtUtil {
    // Access token: 24 hours
    private const val ACCESS_TOKEN_VALIDITY_MS = 24 * 60 * 60 * 1000L

    // Refresh token: 30 days
    const val REFRESH_TOKEN_VALIDITY_MS = 30 * 24 * 60 * 60 * 1000L

    fun generateAccessToken(
        userId: Int,
        userType: String,
        secret: String,
        issuer: String,
        audience: String
    ): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("userType", userType)
            .withClaim("tokenType", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY_MS))
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshTokenString(): String {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis()
    }
}
