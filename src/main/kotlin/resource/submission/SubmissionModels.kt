package resource.submission

import kotlinx.serialization.Serializable
import module.v1.model.SubmissionStatus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// ==================== Request Models ====================

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

// ==================== Response Models ====================

/**
 * Basic submission information
 */
@Serializable
data class SubmissionResponse @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val clientId: Int,
    val clientName: String,
    val agentId: Int?,
    val agentName: String?,
    val status: SubmissionStatus,
    val rejectionReason: String?,
    val adminNotes: String?,
    val pickupLocation: String?,
    val totalPoints: Int?,
    val imageCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val processedAt: Instant?,
    val reviewedAt: Instant?,
    val assignedAt: Instant?,
    val pickedUpAt: Instant?
)

/**
 * Detailed submission information with related data
 */
@Serializable
data class SubmissionDetailResponse @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val clientId: Int,
    val clientName: String,
    val clientNfc: String,
    val agentId: Int?,
    val agentName: String?,
    val status: SubmissionStatus,
    val rejectionReason: String?,
    val adminNotes: String?,
    val pickupLocation: String?,
    val totalPoints: Int?,
    val images: List<SubmissionImageResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val processedAt: Instant?,
    val reviewedAt: Instant?,
    val assignedAt: Instant?,
    val pickedUpAt: Instant?
)

/**
 * Image information within a submission
 */
@Serializable
data class SubmissionImageResponse @OptIn(ExperimentalTime::class) constructor(
    val id: String,
    val url: String,
    val metadata: List<ImageMetadataResponse>,
    val createdAt: Instant
)

/**
 * Metadata information for an image
 */
@Serializable
data class ImageMetadataResponse(
    val id: Int,
    val trashType: String,
    val amount: Int,
    val points: Int // amount * pointsPerUnit
)

/**
 * History entry for a submission
 */
@Serializable
data class SubmissionHistoryResponse @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val oldStatus: String,
    val newStatus: String,
    val changedBy: Int,
    val userType: String,
    val comment: String?,
    val createdAt: Instant
)

/**
 * ML processing status for a submission
 */
@Serializable
data class MLStatusResponse @OptIn(ExperimentalTime::class) constructor(
    val submissionId: Int,
    val status: SubmissionStatus,
    val totalImages: Int,
    val processedImages: Int,
    val failedImages: Int,
    val images: List<MLImageStatus>
)

/**
 * ML processing status for a single image
 */
@Serializable
data class MLImageStatus(
    val id: String,
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED
    val error: String?
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
