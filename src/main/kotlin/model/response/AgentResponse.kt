package model.response

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Agent response
 */
@Serializable
data class AgentResponse @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val name: String,
    val username: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
