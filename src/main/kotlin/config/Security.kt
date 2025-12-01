package config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import getRequiredEnv
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

fun Application.configureSecurity() {
    val adminSecret = getRequiredEnv(Env.ADMIN_JWT_SECRET)
    val clientSecret = getRequiredEnv(Env.CLIENT_JWT_SECRET)
    val agentSecret = getRequiredEnv(Env.AGENT_JWT_SECRET)
    val issuer = getRequiredEnv(Env.JWT_ISSUER)
    val adminAudience = getRequiredEnv(Env.ADMIN_JWT_AUDIENCE)
    val clientAudience = getRequiredEnv(Env.CLIENT_JWT_AUDIENCE)
    val agentAudience = getRequiredEnv(Env.AGENT_JWT_AUDIENCE)
    val realm = "SRW API"

    install(Authentication) {
        // Admin authentication - can access admin, client, and agent resources
        jwt(JwtAuth.ADMIN) {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(adminSecret))
                    .withAudience(adminAudience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null &&
                    credential.payload.getClaim("userType").asString() == "admin"
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }

        // Client authentication - can only access client resources
        jwt(JwtAuth.CLIENT) {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(clientSecret))
                    .withAudience(clientAudience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null &&
                    credential.payload.getClaim("userType").asString() == "client"
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }

        // Agent authentication - can only access agent resources
        jwt(JwtAuth.AGENT) {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(agentSecret))
                    .withAudience(agentAudience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null &&
                    credential.payload.getClaim("userType").asString() == "agent"
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }
    }
}