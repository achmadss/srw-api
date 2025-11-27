package module.service

import UserType
import getRequiredEnv
import io.ktor.http.HttpStatusCode
import module.model.response.BaseResponse
import resource.auth.TokenPair
import module.repository.AdminRepository
import module.repository.AgentRepository
import module.repository.ClientRepository
import module.repository.RefreshTokenRepository
import util.JwtUtil
import util.PasswordUtil
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class AuthService(
    private val adminRepository: AdminRepository,
    private val clientRepository: ClientRepository,
    private val agentRepository: AgentRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    sealed interface AuthType {
        data class Admin(
            val username: String,
            val password: String,
        ): AuthType
        data class Agent(
            val username: String,
            val password: String,
        ): AuthType
        data class Client(
            val nfc: String,
        ): AuthType
    }

    fun login(authType: AuthType): Pair<HttpStatusCode, BaseResponse<TokenPair?>> {
        return transaction {
            val tokenPair = when(authType) {
                is AuthType.Admin -> {
                    val admin = adminRepository.findByUsername(authType.username)
                    if (admin != null && PasswordUtil.verifyPassword(authType.password, admin.password)) {
                        createTokenPair(authType.username, UserType.ADMIN)
                    } else null
                }
                is AuthType.Agent -> {
                    val agent = agentRepository.findByUsername(authType.username)
                    if (agent != null && PasswordUtil.verifyPassword(authType.password, agent.password)) {
                        createTokenPair(authType.username, UserType.AGENT)
                    } else null
                }
                is AuthType.Client -> {
                    val client = clientRepository.findByNfc(authType.nfc)
                    if (client != null) {
                        createTokenPair(authType.nfc, UserType.CLIENT)
                    } else null
                }
            }

            if (tokenPair != null) {
                return@transaction HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = tokenPair
                )
            }

            return@transaction HttpStatusCode.Unauthorized to BaseResponse(
                success = false,
                code = HttpStatusCode.Unauthorized.value,
                data = null
            )
        }
    }

    fun logout(refreshToken: String): Pair<HttpStatusCode, BaseResponse<Unit?>> {
        return transaction {
            val success = refreshTokenRepository.revokeRefreshToken(refreshToken)
            val code = if (success) HttpStatusCode.OK else HttpStatusCode.BadRequest
            code to BaseResponse(
                success = success,
                code = code.value,
                data = null
            )
        }
    }

    fun refresh(refreshToken: String): Pair<HttpStatusCode, BaseResponse<TokenPair?>> {
        return transaction {
            val tokenInfo = refreshTokenRepository.validateRefreshToken(refreshToken)
            if (tokenInfo != null) {
                val userType = UserType(tokenInfo.userType)

                // Get user identifier based on type
                val identifier = when(userType) {
                    UserType.ADMIN -> {
                        val admin = adminRepository.findById(tokenInfo.userId)
                        admin?.username
                    }
                    UserType.CLIENT -> {
                        val client = clientRepository.findById(tokenInfo.userId)
                        client?.nfc
                    }
                    UserType.AGENT -> {
                        val agent = agentRepository.findById(tokenInfo.userId)
                        agent?.username
                    }
                    null -> null
                }

                if (identifier != null && userType != null) {
                    // Revoke the old refresh token
                    refreshTokenRepository.revokeRefreshToken(refreshToken)

                    // Create a new token pair
                    val newTokenPair = createTokenPair(identifier, userType)

                    if (newTokenPair != null) {
                        return@transaction HttpStatusCode.OK to BaseResponse(
                            success = true,
                            code = HttpStatusCode.OK.value,
                            data = newTokenPair
                        )
                    }
                }
            }

            return@transaction HttpStatusCode.Unauthorized to BaseResponse(
                success = false,
                code = HttpStatusCode.Unauthorized.value,
                message = "Invalid or expired refresh token",
                data = null
            )
        }
    }

    private fun createTokenPair(
        identifier: String,
        userType: UserType
    ): TokenPair? {
        return transaction {
            val issuer = getRequiredEnv(Env.JWT_ISSUER)
            val (audience, secret) = when(userType) {
                UserType.ADMIN -> Pair(
                    getRequiredEnv(Env.ADMIN_JWT_AUDIENCE),
                    getRequiredEnv(Env.ADMIN_JWT_SECRET)
                )
                UserType.CLIENT -> Pair(
                    getRequiredEnv(Env.CLIENT_JWT_AUDIENCE),
                    getRequiredEnv(Env.CLIENT_JWT_SECRET)
                )
                UserType.AGENT -> Pair(
                    getRequiredEnv(Env.AGENT_JWT_AUDIENCE),
                    getRequiredEnv(Env.AGENT_JWT_SECRET)
                )
            }
            val userId = when(userType) {
                UserType.ADMIN -> adminRepository.findByUsername(identifier)?.id?.value
                UserType.CLIENT -> clientRepository.findByNfc(identifier)?.id?.value
                UserType.AGENT -> agentRepository.findByUsername(identifier)?.id?.value
            }
            if (userId == null) return@transaction null
            val accessToken = JwtUtil.generateAccessToken(
                userId = userId,
                userType = userType.value,
                secret = secret,
                issuer = issuer,
                audience = audience
            )
            val refreshToken = refreshTokenRepository.createRefreshToken(userId, userType.value)
            return@transaction TokenPair(
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        }
    }
}