package model

import model.response.SubmissionHistoryResponse
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime

/**
 * Table to track all status changes in submissions for audit purposes
 */
@OptIn(ExperimentalTime::class)
object SubmissionHistoryTable : IntIdTable("submission_history") {
    val submission = reference("submission_id", SubmissionTable, onDelete = ReferenceOption.CASCADE)
    val oldStatus = varchar("old_status", 50)
    val newStatus = varchar("new_status", 50)
    val changedBy = integer("changed_by") // User ID (admin, client, or agent)
    val userType = varchar("user_type", 20) // "admin", "client", or "agent"
    val comment = text("comment").nullable() // Optional comment about the change
    val createdAt = timestamp("created_at")
}

/**
 * Entity class for submission history records
 */
@OptIn(ExperimentalTime::class)
class SubmissionHistory(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SubmissionHistory>(SubmissionHistoryTable)

    var submission by Submission referencedOn SubmissionHistoryTable.submission
    var oldStatus by SubmissionHistoryTable.oldStatus
    var newStatus by SubmissionHistoryTable.newStatus
    var changedBy by SubmissionHistoryTable.changedBy
    var userType by SubmissionHistoryTable.userType
    var comment by SubmissionHistoryTable.comment
    var createdAt by SubmissionHistoryTable.createdAt
}

/**
 * Extension function to convert SubmissionHistory entity to SubmissionHistoryResponse
 */
@OptIn(ExperimentalTime::class)
fun SubmissionHistory.toSubmissionHistoryResponse(): SubmissionHistoryResponse {
    return transaction {
        SubmissionHistoryResponse(
            id = this@toSubmissionHistoryResponse.id.value,
            oldStatus = this@toSubmissionHistoryResponse.oldStatus,
            newStatus = this@toSubmissionHistoryResponse.newStatus,
            changedBy = this@toSubmissionHistoryResponse.changedBy,
            userType = this@toSubmissionHistoryResponse.userType,
            comment = this@toSubmissionHistoryResponse.comment,
            createdAt = this@toSubmissionHistoryResponse.createdAt
        )
    }
}
