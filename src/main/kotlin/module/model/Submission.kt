package module.model

import resource.submission.ImageMetadataResponse
import resource.submission.MLImageStatus
import resource.submission.MLStatusResponse
import resource.submission.SubmissionDetailResponse
import resource.submission.SubmissionImageResponse
import resource.submission.SubmissionResponse
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object SubmissionTable: IntIdTable("submissions") {
    val client = reference("client_id", _root_ide_package_.module.model.ClientTable)
    val agent = reference("agent_id", _root_ide_package_.module.model.AgentTable).nullable()
    val status = varchar("status", 50).default("PENDING")
    val rejectionReason = text("rejection_reason").nullable()
    val adminNotes = text("admin_notes").nullable()
    val pickupLocation = text("pickup_location").nullable()
    val totalPoints = integer("total_points").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val processedAt = timestamp("processed_at").nullable()
    val reviewedAt = timestamp("reviewed_at").nullable()
    val assignedAt = timestamp("assigned_at").nullable()
    val pickedUpAt = timestamp("picked_up_at").nullable()
}

@OptIn(ExperimentalTime::class)
class Submission(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<Submission>(_root_ide_package_.module.model.SubmissionTable)
    var client by _root_ide_package_.module.model.Client referencedOn _root_ide_package_.module.model.SubmissionTable.client
    var agent by _root_ide_package_.module.model.Agent optionalReferencedOn _root_ide_package_.module.model.SubmissionTable.agent
    var status by _root_ide_package_.module.model.SubmissionTable.status
    var rejectionReason by _root_ide_package_.module.model.SubmissionTable.rejectionReason
    var adminNotes by _root_ide_package_.module.model.SubmissionTable.adminNotes
    var pickupLocation by _root_ide_package_.module.model.SubmissionTable.pickupLocation
    var totalPoints by _root_ide_package_.module.model.SubmissionTable.totalPoints
    var createdAt by _root_ide_package_.module.model.SubmissionTable.createdAt
    var updatedAt by _root_ide_package_.module.model.SubmissionTable.updatedAt
    var processedAt by _root_ide_package_.module.model.SubmissionTable.processedAt
    var reviewedAt by _root_ide_package_.module.model.SubmissionTable.reviewedAt
    var assignedAt by _root_ide_package_.module.model.SubmissionTable.assignedAt
    var pickedUpAt by _root_ide_package_.module.model.SubmissionTable.pickedUpAt
    val images by _root_ide_package_.module.model.Image referrersOn _root_ide_package_.module.model.ImageTable.submission
    val points by _root_ide_package_.module.model.Point optionalReferrersOn _root_ide_package_.module.model.PointTable.submission
    val history by _root_ide_package_.module.model.SubmissionHistory referrersOn _root_ide_package_.module.model.SubmissionHistoryTable.submission

    /**
     * Get the current status as enum
     */
    fun getStatus(): module.model.SubmissionStatus = _root_ide_package_.module.model.SubmissionStatus.valueOf(status)

    /**
     * Set the status from enum
     */
    fun setStatus(newStatus: module.model.SubmissionStatus) {
        status = newStatus.name
    }
}

/**
 * Extension function to convert Submission entity to SubmissionResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun module.model.Submission.toResponse(): SubmissionResponse {
    return transaction {
        SubmissionResponse(
            id = this@toResponse.id.value,
            clientId = this@toResponse.client.id.value,
            clientName = this@toResponse.client.name,
            agentId = this@toResponse.agent?.id?.value,
            agentName = this@toResponse.agent?.name,
            status = this@toResponse.getStatus(),
            rejectionReason = this@toResponse.rejectionReason,
            adminNotes = this@toResponse.adminNotes,
            pickupLocation = this@toResponse.pickupLocation,
            totalPoints = this@toResponse.totalPoints,
            imageCount = this@toResponse.images.count().toInt(),
            createdAt = this@toResponse.createdAt,
            updatedAt = this@toResponse.updatedAt,
            processedAt = this@toResponse.processedAt,
            reviewedAt = this@toResponse.reviewedAt,
            assignedAt = this@toResponse.assignedAt,
            pickedUpAt = this@toResponse.pickedUpAt
        )
    }
}

/**
 * Extension function to convert Submission entity to SubmissionDetailResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun module.model.Submission.toDetailResponse(): SubmissionDetailResponse {
    return transaction {
        SubmissionDetailResponse(
            id = this@toDetailResponse.id.value,
            clientId = this@toDetailResponse.client.id.value,
            clientName = this@toDetailResponse.client.name,
            clientNfc = this@toDetailResponse.client.nfc,
            agentId = this@toDetailResponse.agent?.id?.value,
            agentName = this@toDetailResponse.agent?.name,
            status = this@toDetailResponse.getStatus(),
            rejectionReason = this@toDetailResponse.rejectionReason,
            adminNotes = this@toDetailResponse.adminNotes,
            pickupLocation = this@toDetailResponse.pickupLocation,
            totalPoints = this@toDetailResponse.totalPoints,
            images = this@toDetailResponse.images.map { image ->
                SubmissionImageResponse(
                    id = image.id.value,
                    url = image.url,
                    metadata = image.metadata.map { metadata ->
                        ImageMetadataResponse(
                            id = metadata.id.value,
                            trashType = metadata.trash.name,
                            amount = metadata.amount,
                            points = metadata.amount * metadata.trash.pointsPerUnit
                        )
                    },
                    createdAt = image.createdAt
                )
            },
            createdAt = this@toDetailResponse.createdAt,
            updatedAt = this@toDetailResponse.updatedAt,
            processedAt = this@toDetailResponse.processedAt,
            reviewedAt = this@toDetailResponse.reviewedAt,
            assignedAt = this@toDetailResponse.assignedAt,
            pickedUpAt = this@toDetailResponse.pickedUpAt
        )
    }
}

/**
 * Extension function to convert List of Submission entities to List of SubmissionResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun List<module.model.Submission>.toResponses(): List<SubmissionResponse> {
    return transaction {
        this@toResponses.map { submission ->
            SubmissionResponse(
                id = submission.id.value,
                clientId = submission.client.id.value,
                clientName = submission.client.name,
                agentId = submission.agent?.id?.value,
                agentName = submission.agent?.name,
                status = submission.getStatus(),
                rejectionReason = submission.rejectionReason,
                adminNotes = submission.adminNotes,
                pickupLocation = submission.pickupLocation,
                totalPoints = submission.totalPoints,
                imageCount = submission.images.count().toInt(),
                createdAt = submission.createdAt,
                updatedAt = submission.updatedAt,
                processedAt = submission.processedAt,
                reviewedAt = submission.reviewedAt,
                assignedAt = submission.assignedAt,
                pickedUpAt = submission.pickedUpAt
            )
        }
    }
}

/**
 * Extension function to convert Submission entity to MLStatusResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun module.model.Submission.toMLStatusResponse(): MLStatusResponse {
    return transaction {
        val images = this@toMLStatusResponse.images.map { image ->
            MLImageStatus(
                id = image.id.value,
                status = image.mlStatus,
                error = image.mlError
            )
        }

        val processedImages = images.count { it.status == _root_ide_package_.module.model.MLStatus.COMPLETED.name }
        val failedImages = images.count { it.status == _root_ide_package_.module.model.MLStatus.FAILED.name }

        MLStatusResponse(
            submissionId = this@toMLStatusResponse.id.value,
            status = this@toMLStatusResponse.getStatus(),
            totalImages = images.size,
            processedImages = processedImages,
            failedImages = failedImages,
            images = images
        )
    }
}