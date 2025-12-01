package model.request

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Request to create a new agent
 */
@Serializable
data class CreateAgentRequest(
    val name: String,
    val username: String,
    val password: String
)

/**
 * Request to update an agent
 */
@Serializable
data class UpdateAgentRequest(
    val name: String?,
    val username: String?,
    val password: String?
)

