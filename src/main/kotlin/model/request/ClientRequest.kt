package model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRequest(
    val nfc: String,
    val name: String,
    val address: String,
)