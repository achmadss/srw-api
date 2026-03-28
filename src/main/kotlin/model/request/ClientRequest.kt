package model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRequest(
    val nfc: String,
    val name: String,
    val address: String? = null,
    val latitude: Float? = null,
    val longitude: Float? = null,
)

@Serializable
data class UpdateClientRequest(
    val nfc: String? = null,
    val name: String? = null,
    val address: String? = null,
    val latitude: Float? = null,
    val longitude: Float? = null,
)

@Serializable
data class SetAddressRequest(
    val address: String,
    val latitude: Float? = null,
    val longitude: Float? = null,
)