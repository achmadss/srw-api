package model.request

import kotlinx.serialization.Serializable

/**
 * Request to create a new client
 */
@Serializable
data class CreateClientRequest(
    val nfc: String,
    val name: String,
    val address: String,
)

/**
 * Request to update a client
 */
@Serializable
data class UpdateClientRequest(
    val nfc: String? = null,
    val name: String? = null,
    val address: String? = null,
)