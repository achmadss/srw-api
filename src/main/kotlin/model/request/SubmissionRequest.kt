package model.request

import kotlinx.serialization.Serializable

/**
 * Request to review (approve/reject) a submission
 */
@Serializable
data class ReviewSubmissionRequest(
    val approved: Boolean,
    val rejectionReason: String? = null, // Required if approved = false
    val adminNotes: String? = null
)

/**
 * Request to assign an agent to a submission
 */
@Serializable
data class AssignAgentRequest(
    val agentId: Int
)

/**
 * Request to confirm pickup by agent
 */
@Serializable
data class ConfirmPickupRequest(
    val notes: String? = null
)

/**
 * Request to manually add metadata to an image (for failed ML processing)
 */
@Serializable
data class ManualMetadataRequest(
    val metadata: List<ManualMetadataItem>
)

/**
 * Single metadata item for manual input
 */
@Serializable
data class ManualMetadataItem(
    val trashTypeName: String,
    val amount: Int
)
