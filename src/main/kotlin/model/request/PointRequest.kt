package model.request

import kotlinx.serialization.Serializable

@Serializable
data class ClaimPointsRequest(
    val amount: Int
)