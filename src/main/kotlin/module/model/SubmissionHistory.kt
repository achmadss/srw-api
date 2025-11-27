package module.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime
import resource.submission.SubmissionHistoryResponse

/**
 * Table to track all status changes in submissions for audit purposes
 */
@OptIn(ExperimentalTime::class)
object SubmissionHistoryTable : IntIdTable("submission_history") {
    val submission = reference("submission_id", _root_ide_package_.module.model.SubmissionTable, onDelete = ReferenceOption.CASCADE)
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
    companion object : IntEntityClass<SubmissionHistory>(_root_ide_package_.module.model.SubmissionHistoryTable)

    var submission by _root_ide_package_.module.model.Submission referencedOn _root_ide_package_.module.model.SubmissionHistoryTable.submission
    var oldStatus by _root_ide_package_.module.model.SubmissionHistoryTable.oldStatus
    var newStatus by _root_ide_package_.module.model.SubmissionHistoryTable.newStatus
    var changedBy by _root_ide_package_.module.model.SubmissionHistoryTable.changedBy
    var userType by _root_ide_package_.module.model.SubmissionHistoryTable.userType
    var comment by _root_ide_package_.module.model.SubmissionHistoryTable.comment
    var createdAt by _root_ide_package_.module.model.SubmissionHistoryTable.createdAt
}

/**
 * Extension function to convert SubmissionHistory entity to SubmissionHistoryResponse
 */
@OptIn(ExperimentalTime::class)
fun module.model.SubmissionHistory.toResponse(): SubmissionHistoryResponse {
    return transaction {
        SubmissionHistoryResponse(
            id = this@toResponse.id.value,
            oldStatus = this@toResponse.oldStatus,
            newStatus = this@toResponse.newStatus,
            changedBy = this@toResponse.changedBy,
            userType = this@toResponse.userType,
            comment = this@toResponse.comment,
            createdAt = this@toResponse.createdAt
        )
    }
}
