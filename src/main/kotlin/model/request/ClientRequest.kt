package model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateClientRequest(
    val nfc: String,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class UpdateClientRequest(
    val nfc: String? = null,
    val name: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class SetAddressRequest(
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)