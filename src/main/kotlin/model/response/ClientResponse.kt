package model.response

import kotlinx.serialization.Serializable

@Serializable
data class ClientResponse(
    val id: Int,
    val nfc: String,
    val name: String,
    val address: String,
    val totalPoints: Int
)