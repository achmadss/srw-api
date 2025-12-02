package model.request

import kotlinx.serialization.Serializable


/**
 * Request to create a new trash type
 */
@Serializable
data class CreateTrashRequest(
    val name: String,
    val pointsPerUnit: Int
)

/**
 * Request to update a trash type (only pointsPerUnit can be updated, name is primary key)
 */
@Serializable
data class UpdateTrashRequest(
    val pointsPerUnit: Int?
)
