package model.request

import kotlinx.serialization.Serializable

@Serializable
data class BasicAuthRequest(
    val username: String,
    val password: String,
)

@Serializable
data class NfcAuthRequest(
    val nfc: String,
)

@Serializable
data class RefreshTokenAuthRequest(
    val refreshToken: String,
)

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String
)