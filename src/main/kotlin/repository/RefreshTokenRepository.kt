package repository

import model.RefreshToken
import model.RefreshTokenTable
import org.jetbrains.exposed.v1.core.and
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import util.JwtUtil
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTime::class)
class RefreshTokenRepository {

    fun createRefreshToken(userId: Int, userType: String): String {
        val tokenString = JwtUtil.generateRefreshTokenString()
        val now = Clock.System.now()
        val expiresAt = now + JwtUtil.REFRESH_TOKEN_VALIDITY_MS.milliseconds

        RefreshToken.new {
            this.token = tokenString
            this.userId = userId
            this.userType = userType
            this.expiresAt = expiresAt
            this.isRevoked = false
            this.createdAt = now
        }

        return tokenString
    }

    fun validateRefreshToken(tokenString: String): RefreshTokenInfo? {
        val refreshToken = RefreshToken.find { RefreshTokenTable.token eq tokenString }.firstOrNull()

        if (refreshToken == null || refreshToken.isRevoked) {
            return null
        }

        val now = Clock.System.now()
        if (refreshToken.expiresAt < now) {
            return null
        }

        return RefreshTokenInfo(
            userId = refreshToken.userId,
            userType = refreshToken.userType
        )
    }

    fun revokeRefreshToken(tokenString: String): Boolean {
        val refreshToken = RefreshToken.find { RefreshTokenTable.token eq tokenString }.firstOrNull()
        if (refreshToken != null) {
            refreshToken.isRevoked = true
            return true
        } else {
            return false
        }
    }

    fun revokeAllUserTokens(userId: Int, userType: String) {
        RefreshToken.find {
            (RefreshTokenTable.userId eq userId) and
            (RefreshTokenTable.userType eq userType) and
            (RefreshTokenTable.isRevoked eq false)
        }.forEach {
            it.isRevoked = true
        }
    }

    fun cleanupExpiredTokens() {
        val now = Clock.System.now()
        RefreshToken.find { RefreshTokenTable.expiresAt less now }.forEach {
            it.delete()
        }
    }

    data class RefreshTokenInfo(
        val userId: Int,
        val userType: String
    )
}
