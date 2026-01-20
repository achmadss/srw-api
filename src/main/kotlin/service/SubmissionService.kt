package service

import UserType
import io.ktor.http.HttpStatusCode
import model.MLStatus
import model.SubmissionStatus
import model.request.ManualMetadataItem
import model.response.MLStatusResponse
import model.response.SubmissionDetailResponse
import model.response.SubmissionHistoryResponse
import model.response.base.BaseResponse
import model.response.base.PaginatedResponse
import model.toMLStatusResponse
import model.toSubmissionDetailResponse
import model.toSubmissionHistoryResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.ImageRepository
import repository.MetadataRepository
import repository.PointRepository
import repository.SubmissionHistoryRepository
import repository.SubmissionRepository
import repository.TrashRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SubmissionService(
    private val submissionRepository: SubmissionRepository,
    private val submissionHistoryRepository: SubmissionHistoryRepository,
    private val imageRepository: ImageRepository,
    private val metadataRepository: MetadataRepository,
    private val trashRepository: TrashRepository,
    private val pointRepository: PointRepository,
    private val imageService: ImageService,
    private val rabbitMQClient: util.RabbitMQClient
) {

    /**
     * Create a new submission for a client with image uploads
     */
    fun createWithImages(
        clientId: Int,
        images: List<ImageUploadData>
    ): Pair<HttpStatusCode, BaseResponse<SubmissionDetailResponse>> {
        return transaction {
            // Validate that at least one image is provided
            if (images.isEmpty()) {
                return@transaction HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = "At least one image is required",
                    data = null
                )
            }

            var submissionId: Int? = null

            try {
                // Create the submission first
                val (_, id) = submissionRepository.create(clientId)
                submissionId = id

                // Upload images to MinIO and create database records
                imageService.uploadMultipleImages(images, submissionId)

                // Create history entry
                submissionHistoryRepository.create(
                    submissionId = submissionId,
                    oldStatus = SubmissionStatus.PENDING,
                    newStatus = SubmissionStatus.PENDING,
                    changedBy = clientId,
                    userType = UserType.CLIENT.value,
                    comment = "Submission created with ${images.size} image(s)"
                )

                // Fetch updated submission with images and extract data within transaction
                val (imageMessages, submission) = submissionRepository.getSubmissionWithImages(submissionId)

                // Publish ML job to RabbitMQ
                rabbitMQClient.publishMLJob(submissionId, imageMessages)

                HttpStatusCode.Created to BaseResponse(
                    success = true,
                    code = HttpStatusCode.Created.value,
                    message = "Submission created successfully",
                    data = submission.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) }
                )
            } catch (e: Exception) {
                // If image upload fails, clean up the submission
                submissionId?.let { id ->
                    try {
                        submissionRepository.delete(id)
                    } catch (cleanupError: Exception) {
                        // Log cleanup error but don't override original error
                        println("Failed to cleanup submission after image upload error: ${cleanupError.message}")
                    }
                }

                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = "Failed to upload images: ${e.message}",
                    data = null
                )
            }
        }
    }

    /**
     * Get submission by ID
     */
    fun getById(id: Int): Pair<HttpStatusCode, BaseResponse<SubmissionDetailResponse?>> {
        return transaction {
            val submission = submissionRepository.findById(id)?.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) }
            if (submission != null) {
                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = submission
                )
            } else {
                HttpStatusCode.NotFound to BaseResponse(
                    success = false,
                    code = HttpStatusCode.NotFound.value,
                    message = "Submission with id $id not found",
                    data = null
                )
            }
        }
    }

    /**
     * Get paginated submissions
     */
    fun getPaginated(
        page: Int,
        pageSize: Int,
        status: SubmissionStatus? = null
    ): Pair<HttpStatusCode, BaseResponse<PaginatedResponse<SubmissionDetailResponse>>> {
        return transaction {
            val validPage = if (page >= 1) page else 1
            val validPageSize = if (pageSize >= 1) pageSize else 20

            val submissions = if (status != null) {
                submissionRepository.findByStatus(status)
                    .drop((validPage - 1) * validPageSize)
                    .take(validPageSize)
            } else {
                submissionRepository.findAllPaginated(validPage, validPageSize)
            }

            val total = if (status != null) {
                submissionRepository.totalCountByStatus(status)
            } else {
                submissionRepository.totalCount()
            }
            val totalPages = if (total == 0) 1 else ((total + validPageSize - 1) / validPageSize)

            HttpStatusCode.OK to BaseResponse(
                success = true,
                code = HttpStatusCode.OK.value,
                data = PaginatedResponse(
                    data = submissions.map { it.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) } },
                    page = validPage,
                    pageSize = validPageSize,
                    total = total,
                    totalPages = totalPages
                )
            )
        }
    }

    /**
     * Get submissions by client with pagination
     */
    fun getByClientPaginated(
        clientId: Int,
        page: Int,
        pageSize: Int,
        status: SubmissionStatus? = null
    ): Pair<HttpStatusCode, BaseResponse<PaginatedResponse<SubmissionDetailResponse>>> {
        return transaction {
            try {
                val validPage = if (page >= 1) page else 1
                val validPageSize = if (pageSize >= 1) pageSize else 20

                val submissions = if (status != null) {
                    submissionRepository.findByClientAndStatusPaginated(clientId, status, validPage, validPageSize)
                } else {
                    submissionRepository.findByClientPaginated(clientId, validPage, validPageSize)
                }

                val total = if (status != null) {
                    submissionRepository.totalCountByClientAndStatus(clientId, status)
                } else {
                    submissionRepository.totalCountByClient(clientId)
                }

                val totalPages = if (total == 0) 1 else ((total + validPageSize - 1) / validPageSize)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = PaginatedResponse(
                        data = submissions.map { it.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) } },
                        page = validPage,
                        pageSize = validPageSize,
                        total = total,
                        totalPages = totalPages
                    )
                )
            } catch (e: Exception) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to get submissions",
                    data = null
                )
            }
        }
    }

    /**
     * Get submissions by agent with pagination
     */
    fun getByAgentPaginated(
        agentId: Int,
        page: Int,
        pageSize: Int,
        status: SubmissionStatus? = null
    ): Pair<HttpStatusCode, BaseResponse<PaginatedResponse<SubmissionDetailResponse>>> {
        return transaction {
            try {
                val validPage = if (page >= 1) page else 1
                val validPageSize = if (pageSize >= 1) pageSize else 20

                val submissions = if (status != null) {
                    submissionRepository.findByAgentAndStatusPaginated(agentId, status, validPage, validPageSize)
                } else {
                    submissionRepository.findByAgentPaginated(agentId, validPage, validPageSize)
                }

                val total = if (status != null) {
                    submissionRepository.totalCountByAgentAndStatus(agentId, status)
                } else {
                    submissionRepository.totalCountByAgent(agentId)
                }

                val totalPages = if (total == 0) 1 else ((total + validPageSize - 1) / validPageSize)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = PaginatedResponse(
                        data = submissions.map { it.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) } },
                        page = validPage,
                        pageSize = validPageSize,
                        total = total,
                        totalPages = totalPages
                    )
                )
            } catch (e: Exception) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to get submissions",
                    data = null
                )
            }
        }
    }

    /**
     * Review submission (approve or reject)
     */
    fun review(
        id: Int,
        adminId: Int,
        approved: Boolean,
        rejectionReason: String? = null,
        adminNotes: String? = null
    ): Pair<HttpStatusCode, BaseResponse<SubmissionDetailResponse>> {
        return transaction {
            try {
                val submission = submissionRepository.findById(id)
                    ?: return@transaction HttpStatusCode.NotFound to BaseResponse(
                        success = false,
                        code = HttpStatusCode.NotFound.value,
                        message = "Submission not found",
                        data = null
                    )

                val currentStatus = submission.getStatus()

                // Validate status transition
                if (currentStatus != SubmissionStatus.AWAITING_REVIEW) {
                    return@transaction HttpStatusCode.BadRequest to BaseResponse(
                        success = false,
                        code = HttpStatusCode.BadRequest.value,
                        message = "Submission must be in AWAITING_REVIEW status to be reviewed",
                        data = null
                    )
                }

                // Validate rejection reason
                if (!approved && rejectionReason.isNullOrBlank()) {
                    return@transaction HttpStatusCode.BadRequest to BaseResponse(
                        success = false,
                        code = HttpStatusCode.BadRequest.value,
                        message = "Rejection reason is required when rejecting a submission",
                        data = null
                    )
                }

                val newStatus = if (approved) SubmissionStatus.APPROVED else SubmissionStatus.REJECTED
                val now = Clock.System.now()

                // Update status
                submissionRepository.updateStatus(id, newStatus, now)

                // Update fields based on approval
                if (!approved && rejectionReason != null) {
                    submissionRepository.updateRejectionReason(id, rejectionReason)
                }

                if (!adminNotes.isNullOrBlank()) {
                    submissionRepository.updateAdminNotes(id, adminNotes)
                }

                // Create history entry
                submissionHistoryRepository.create(
                    submissionId = id,
                    oldStatus = currentStatus,
                    newStatus = newStatus,
                    changedBy = adminId,
                    userType = UserType.ADMIN.value,
                    comment = if (approved) adminNotes else rejectionReason
                )

                val updatedSubmission = submissionRepository.findById(id)?.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) }!!
                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = if (approved) "Submission approved" else "Submission rejected",
                    data = updatedSubmission
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = e.message ?: "Failed to review submission",
                    data = null
                )
            }
        }
    }

    /**
     * Assign agent to submission
     */
    fun assignAgent(
        id: Int,
        adminId: Int,
        agentId: Int
    ): Pair<HttpStatusCode, BaseResponse<SubmissionDetailResponse>> {
        return transaction {
            try {
                val submission = submissionRepository.findById(id)
                    ?: return@transaction HttpStatusCode.NotFound to BaseResponse(
                        success = false,
                        code = HttpStatusCode.NotFound.value,
                        message = "Submission not found",
                        data = null
                    )

                val currentStatus = submission.getStatus()

                // Validate status
                if (currentStatus != SubmissionStatus.APPROVED) {
                    return@transaction HttpStatusCode.BadRequest to BaseResponse(
                        success = false,
                        code = HttpStatusCode.BadRequest.value,
                        message = "Only approved submissions can be assigned to agents",
                        data = null
                    )
                }

                val now = Clock.System.now()

                // Use client's address as pickup location
                val pickupLocation = submission.client.address

                // Assign agent with client's address as pickup location
                submissionRepository.assignAgent(id, agentId, pickupLocation)

                // Update status
                submissionRepository.updateStatus(id, SubmissionStatus.ASSIGNED, now)

                // Create history entry
                submissionHistoryRepository.create(
                    submissionId = id,
                    oldStatus = currentStatus,
                    newStatus = SubmissionStatus.ASSIGNED,
                    changedBy = adminId,
                    userType = UserType.ADMIN.value,
                    comment = "Agent assigned: $agentId, pickup location: $pickupLocation"
                )

                val updatedSubmission = submissionRepository.findById(id)?.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) }!!
                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Agent assigned successfully",
                    data = updatedSubmission
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = e.message ?: "Failed to assign agent",
                    data = null
                )
            }
        }
    }

    /**
     * Confirm pickup by agent
     */
    fun confirmPickup(
        id: Int,
        agentId: Int,
        notes: String? = null
    ): Pair<HttpStatusCode, BaseResponse<SubmissionDetailResponse>> {
        return transaction {
            try {
                val submission = submissionRepository.findById(id)
                    ?: return@transaction HttpStatusCode.NotFound to BaseResponse(
                        success = false,
                        code = HttpStatusCode.NotFound.value,
                        message = "Submission not found",
                        data = null
                    )

                val currentStatus = submission.getStatus()

                // Validate status
                if (currentStatus != SubmissionStatus.ASSIGNED) {
                    return@transaction HttpStatusCode.BadRequest to BaseResponse(
                        success = false,
                        code = HttpStatusCode.BadRequest.value,
                        message = "Submission must be assigned before pickup can be confirmed",
                        data = null
                    )
                }

                // Validate agent
                if (submission.agent?.id?.value != agentId) {
                    return@transaction HttpStatusCode.Forbidden to BaseResponse(
                        success = false,
                        code = HttpStatusCode.Forbidden.value,
                        message = "Only the assigned agent can confirm pickup",
                        data = null
                    )
                }

                val now = Clock.System.now()

                // Update status
                submissionRepository.updateStatus(id, SubmissionStatus.PICKED_UP, now)

                // Create history entry
                submissionHistoryRepository.create(
                    submissionId = id,
                    oldStatus = currentStatus,
                    newStatus = SubmissionStatus.PICKED_UP,
                    changedBy = agentId,
                    userType = UserType.AGENT.value,
                    comment = notes ?: "Pickup confirmed"
                )

                // Mark as completed
                submissionRepository.updateStatus(id, SubmissionStatus.COMPLETED, now)

                submissionHistoryRepository.create(
                    submissionId = id,
                    oldStatus = SubmissionStatus.PICKED_UP,
                    newStatus = SubmissionStatus.COMPLETED,
                    changedBy = agentId,
                    userType = UserType.AGENT.value,
                    comment = "Submission completed"
                )

                // Award points only upon completion (pickup finished)
                pointRepository.create(
                    clientId = submission.client.id.value,
                    submissionId = id,
                    amount = submission.calculateTotalPoints()
                )

                val updatedSubmission = submissionRepository.findById(id)?.toSubmissionDetailResponse { imageId -> imageService.getImageUrl(imageId) }!!
                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Pickup confirmed and submission completed",
                    data = updatedSubmission
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = e.message ?: "Failed to confirm pickup",
                    data = null
                )
            }
        }
    }

    /**
     * Get submission history
     */
    fun getHistory(id: Int): Pair<HttpStatusCode, BaseResponse<List<SubmissionHistoryResponse>>> {
        return transaction {
            try {
                val history = submissionHistoryRepository.findBySubmission(id)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = history.map { it.toSubmissionHistoryResponse() }
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = e.message ?: "Failed to get history",
                    data = null
                )
            }
        }
    }

    /**
     * Get ML processing status for a submission
     */
    fun getMLStatus(id: Int): Pair<HttpStatusCode, BaseResponse<MLStatusResponse?>> {
        return transaction {
            try {
                val submission = submissionRepository.findById(id)
                    ?: return@transaction HttpStatusCode.NotFound to BaseResponse(
                        success = false,
                        code = HttpStatusCode.NotFound.value,
                        message = "Submission not found",
                        data = null
                    )

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = submission.toMLStatusResponse()
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = e.message ?: "Failed to get ML status",
                    data = null
                )
            }
        }
    }

    /**
     * Update metadata for an image (replaces existing metadata)
     */
    fun updateMetadata(
        submissionId: Int,
        imageId: String,
        metadata: List<ManualMetadataItem>
    ): Pair<HttpStatusCode, BaseResponse<Unit>> {
        return transaction {
            try {
                val image = imageRepository.findById(imageId)
                    ?: return@transaction HttpStatusCode.NotFound to BaseResponse(
                        success = false,
                        code = HttpStatusCode.NotFound.value,
                        message = "Image not found",
                        data = null
                    )

                // Validate image belongs to submission
                if (image.submission.id.value != submissionId) {
                    return@transaction HttpStatusCode.BadRequest to BaseResponse(
                        success = false,
                        code = HttpStatusCode.BadRequest.value,
                        message = "Image does not belong to this submission",
                        data = null
                    )
                }

                // Delete existing metadata for this image
                val existingMetadata = metadataRepository.findByImage(imageId)
                existingMetadata.forEach { metadataItem ->
                    metadataRepository.delete(metadataItem.id.value)
                }

                // Create new metadata records
                metadata.forEach { item ->
                    trashRepository.findByName(item.trashTypeName)
                        ?: return@transaction HttpStatusCode.BadRequest to BaseResponse(
                            success = false,
                            code = HttpStatusCode.BadRequest.value,
                            message = "Trash type '${item.trashTypeName}' not found",
                            data = null
                        )

                    metadataRepository.create(
                        amount = item.amount,
                        imageId = imageId,
                        trashName = item.trashTypeName
                    )
                }

                // Mark image as completed
                imageRepository.updateMLStatus(imageId, MLStatus.COMPLETED, null)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Metadata updated successfully",
                    data = Unit
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = e.message ?: "Failed to update metadata",
                    data = null
                )
            }
        }
    }
}