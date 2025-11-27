package resource.client

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRequest(
    val nfc: String,
    val name: String,
    val address: String,
)

@Serializable
data class ClientResponse(
    val nfc: String,
    val name: String,
    val address: String,
    val totalPoints: Int = 0
)