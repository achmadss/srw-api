package model.response

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Trash type response
 */
@Serializable
data class TrashResponse @OptIn(ExperimentalTime::class) constructor(
    val name: String,
    val pointsPerUnit: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
