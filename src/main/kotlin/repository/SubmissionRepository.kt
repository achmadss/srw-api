package repository

import model.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SubmissionRepository {

    /**
     * Create a new submission with PENDING status
     */
    fun create(
        clientId: Int,
        address: String? = null,
        latitude: Float? = null,
        longitude: Float? = null
    ): Pair<Submission, Int> {
        val client = Client.findById(clientId)
            ?: throw IllegalArgumentException("Client with id $clientId not found")

        val now = Clock.System.now()
        val submission = Submission.new {
            this.client = client
            this.agent = null
            this.status = SubmissionStatus.PENDING.name
            this.submissionAddress = address
            this.submissionLatitude = latitude
            this.submissionLongitude = longitude
            this.createdAt = now
            this.updatedAt = now
        }
        // Extract ID within transaction to avoid detached entity access
        return submission to submission.id.value
    }

    /**
     * Find submission by ID
     */
    fun findById(id: Int): Submission? {
        return Submission.findById(id)
    }

    /**
     * Find all submissions
     */
    fun findAll(): List<Submission> {
        return Submission.all().orderBy(SubmissionTable.createdAt to SortOrder.DESC).toList()
    }

    /**
     * Find submissions with pagination
     */
    fun findAllPaginated(page: Int, pageSize: Int): List<Submission> {
        return Submission.all()
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
    }

    /**
     * Get total count of submissions
     */
    fun totalCount(): Int {
        return Submission.all().count().toInt()
    }

    /**
     * Find submissions by client
     */
    fun findByClient(clientId: Int): List<Submission> {
        val client = Client.findById(clientId)
            ?: throw IllegalArgumentException("Client with id $clientId not found")
        return client.submissions.orderBy(SubmissionTable.createdAt to SortOrder.DESC).toList()
    }

    /**
     * Find submissions by client and status
     */
    fun findByClientAndStatus(clientId: Int, status: SubmissionStatus): List<Submission> {
        Client.findById(clientId)
            ?: throw IllegalArgumentException("Client with id $clientId not found")
        return Submission.find { SubmissionTable.client eq clientId and (SubmissionTable.status eq status.name) }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .toList()
    }

    /**
     * Find submissions by agent
     */
    fun findByAgent(agentId: Int): List<Submission> {
        val agent = Agent.findById(agentId)
            ?: throw IllegalArgumentException("Agent with id $agentId not found")
        return agent.submissions.orderBy(SubmissionTable.createdAt to SortOrder.DESC).toList()
    }

    /**
     * Find submissions by agent and status
     */
    fun findByAgentAndStatus(agentId: Int, status: SubmissionStatus): List<Submission> {
        return Submission.find { SubmissionTable.agent eq agentId and (SubmissionTable.status eq status.name) }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .toList()
    }

    /**
     * Find submissions by client with pagination
     */
    fun findByClientPaginated(clientId: Int, page: Int, pageSize: Int): List<Submission> {
        return Submission.find { SubmissionTable.client eq clientId }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
    }

    /**
     * Find submissions by client and status with pagination
     */
    fun findByClientAndStatusPaginated(clientId: Int, status: SubmissionStatus, page: Int, pageSize: Int): List<Submission> {
        return Submission.find { SubmissionTable.client eq clientId and (SubmissionTable.status eq status.name) }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
    }

    /**
     * Find submissions by agent with pagination
     */
    fun findByAgentPaginated(agentId: Int, page: Int, pageSize: Int): List<Submission> {
        return Submission.find { SubmissionTable.agent eq agentId }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
    }

    /**
     * Find submissions by agent and status with pagination
     */
    fun findByAgentAndStatusPaginated(agentId: Int, status: SubmissionStatus, page: Int, pageSize: Int): List<Submission> {
        return Submission.find { SubmissionTable.agent eq agentId and (SubmissionTable.status eq status.name) }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()
    }

    /**
     * Get total count of submissions by client
     */
    fun totalCountByClient(clientId: Int): Int {
        return Submission.find { SubmissionTable.client eq clientId }.count().toInt()
    }

    /**
     * Get total count of submissions by client and status
     */
    fun totalCountByClientAndStatus(clientId: Int, status: SubmissionStatus): Int {
        return Submission.find { SubmissionTable.client eq clientId and (SubmissionTable.status eq status.name) }.count().toInt()
    }

    /**
     * Get total count of submissions by agent
     */
    fun totalCountByAgent(agentId: Int): Int {
        return Submission.find { SubmissionTable.agent eq agentId }.count().toInt()
    }

    /**
     * Get total count of submissions by agent and status
     */
    fun totalCountByAgentAndStatus(agentId: Int, status: SubmissionStatus): Int {
        return Submission.find { SubmissionTable.agent eq agentId and (SubmissionTable.status eq status.name) }.count().toInt()
    }

    /**
     * Find submissions by status
     */
    fun findByStatus(status: SubmissionStatus): List<Submission> {
        return Submission.find { SubmissionTable.status eq status.name }
            .orderBy(SubmissionTable.createdAt to SortOrder.DESC)
            .toList()
    }

    /**
     * Get total count of submissions by status
     */
    fun totalCountByStatus(status: SubmissionStatus): Int {
        return Submission.find { SubmissionTable.status eq status.name }.count().toInt()
    }

    /**
     * Update submission status
     */
    fun updateStatus(
        id: Int,
        newStatus: SubmissionStatus,
        timestamp: kotlin.time.Instant? = null
    ): Submission {
        val submission = Submission.findById(id)
            ?: throw IllegalArgumentException("Submission with id $id not found")

        submission.setStatus(newStatus)
        submission.updatedAt = Clock.System.now()

        // Update specific timestamp based on status
        timestamp?.let {
            when (newStatus) {
                SubmissionStatus.ML_PROCESSING, SubmissionStatus.AWAITING_REVIEW -> submission.processedAt = it
                SubmissionStatus.APPROVED, SubmissionStatus.REJECTED -> submission.reviewedAt = it
                SubmissionStatus.ASSIGNED -> submission.assignedAt = it
                SubmissionStatus.PICKED_UP -> submission.pickedUpAt = it
                else -> {} // No specific timestamp for other statuses
            }
        }

        return submission
    }

    /**
     * Assign agent to submission
     */
    fun assignAgent(id: Int, agentId: Int): Submission {
        val submission = Submission.findById(id)
            ?: throw IllegalArgumentException("Submission with id $id not found")
        val agent = Agent.findById(agentId)
            ?: throw IllegalArgumentException("Agent with id $agentId not found")

        submission.agent = agent
        submission.updatedAt = Clock.System.now()
        return submission
    }

    /**
     * Update rejection reason
     */
    fun updateRejectionReason(id: Int, reason: String): Submission {
        val submission = Submission.findById(id)
            ?: throw IllegalArgumentException("Submission with id $id not found")

        submission.rejectionReason = reason
        submission.updatedAt = Clock.System.now()
        return submission
    }

    /**
     * Update admin notes
     */
    fun updateAdminNotes(id: Int, notes: String): Submission {
        val submission = Submission.findById(id)
            ?: throw IllegalArgumentException("Submission with id $id not found")

        submission.adminNotes = notes
        submission.updatedAt = Clock.System.now()
        return submission
    }

    /**
     * Delete submission
     */
    fun delete(id: Int) {
        val submission = Submission.findById(id)
            ?: throw IllegalArgumentException("Submission with id $id not found")
        submission.delete()
    }

    /**
     * Get submission with images, extracting all data within transaction to avoid detached entity access
     */
    fun getSubmissionWithImages(id: Int): Pair<List<util.ImageMessage>, Submission> {
        val submission = Submission.findById(id)
            ?: throw IllegalArgumentException("Submission with id $id not found")

        // Extract image messages for RabbitMQ
        val imageMessages = submission.images.map { image ->
            util.ImageMessage(
                id = image.id.value
            )
        }

        // Use extension function for response
        return imageMessages to submission
    }
}