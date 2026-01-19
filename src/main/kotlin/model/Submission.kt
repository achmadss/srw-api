package model

import model.response.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object SubmissionTable: IntIdTable("submissions") {
    val client = reference("client_id", ClientTable)
    val agent = reference("agent_id", AgentTable).nullable()
    val status = varchar("status", 50).default("PENDING")
    val rejectionReason = text("rejection_reason").nullable()
    val adminNotes = text("admin_notes").nullable()
    val pickupLocation = text("pickup_location").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val processedAt = timestamp("processed_at").nullable()
    val reviewedAt = timestamp("reviewed_at").nullable()
    val assignedAt = timestamp("assigned_at").nullable()
    val pickedUpAt = timestamp("picked_up_at").nullable()
}

@OptIn(ExperimentalTime::class)
class Submission(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<Submission>(SubmissionTable)
    var client by Client referencedOn SubmissionTable.client
    var agent by Agent optionalReferencedOn SubmissionTable.agent
    var status by SubmissionTable.status
    var rejectionReason by SubmissionTable.rejectionReason
    var adminNotes by SubmissionTable.adminNotes
    var pickupLocation by SubmissionTable.pickupLocation
    var createdAt by SubmissionTable.createdAt
    var updatedAt by SubmissionTable.updatedAt
    var processedAt by SubmissionTable.processedAt
    var reviewedAt by SubmissionTable.reviewedAt
    var assignedAt by SubmissionTable.assignedAt
    var pickedUpAt by SubmissionTable.pickedUpAt
    val images by Image referrersOn ImageTable.submission
    val points by Point optionalReferrersOn PointTable.submission
    val history by SubmissionHistory referrersOn SubmissionHistoryTable.submission

    /**
     * Get the current status as enum
     */
    fun getStatus(): SubmissionStatus = SubmissionStatus.valueOf(status)

    /**
     * Set the status from enum
     */
    fun setStatus(newStatus: SubmissionStatus) {
        status = newStatus.name
    }

    fun calculateTotalPoints(): Int {
        return images.flatMap { it.metadata }.sumOf { it.amount * it.trash.pointsPerUnit }
    }
}

/**
 * Extension function to convert Submission entity to SubmissionDetailResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun Submission.toSubmissionDetailResponse(imageUrlProvider: ((String) -> String)? = null): SubmissionDetailResponse {
    return transaction {
        SubmissionDetailResponse(
            id = this@toSubmissionDetailResponse.id.value,
            clientId = this@toSubmissionDetailResponse.client.id.value,
            clientName = this@toSubmissionDetailResponse.client.name,
            clientNfc = this@toSubmissionDetailResponse.client.nfc,
            agentId = this@toSubmissionDetailResponse.agent?.id?.value,
            agentName = this@toSubmissionDetailResponse.agent?.name,
            status = this@toSubmissionDetailResponse.getStatus(),
            rejectionReason = this@toSubmissionDetailResponse.rejectionReason,
            adminNotes = this@toSubmissionDetailResponse.adminNotes,
            pickupLocation = this@toSubmissionDetailResponse.pickupLocation,
            totalPoints = this@toSubmissionDetailResponse.calculateTotalPoints(),
            images = this@toSubmissionDetailResponse.images.map { image ->
                SubmissionImageResponse(
                    id = image.id.value,
                    url = imageUrlProvider?.invoke(image.id.value) ?: image.id.value,
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
            createdAt = this@toSubmissionDetailResponse.createdAt,
            updatedAt = this@toSubmissionDetailResponse.updatedAt,
            processedAt = this@toSubmissionDetailResponse.processedAt,
            reviewedAt = this@toSubmissionDetailResponse.reviewedAt,
            assignedAt = this@toSubmissionDetailResponse.assignedAt,
            pickedUpAt = this@toSubmissionDetailResponse.pickedUpAt
        )
    }
}

/**
 * Extension function to convert Submission entity to MLStatusResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun Submission.toMLStatusResponse(): MLStatusResponse {
    return transaction {
        val images = this@toMLStatusResponse.images.map { image ->
            MLImageStatus(
                id = image.id.value,
                status = image.mlStatus,
                error = image.mlError
            )
        }

        val processedImages = images.count { it.status == MLStatus.COMPLETED.name }
        val failedImages = images.count { it.status == MLStatus.FAILED.name }

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