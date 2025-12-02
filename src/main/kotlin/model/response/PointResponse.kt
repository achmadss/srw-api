package model.response

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class PointResponse @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val amount: Int,
    val createdAt: Instant,
    val submissionId: Int?
)