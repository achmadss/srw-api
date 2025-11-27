package module.v1.repository

import module.v1.model.Submission
import module.v1.model.SubmissionHistory
import module.v1.model.SubmissionHistoryTable
import module.v1.model.SubmissionStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SubmissionHistoryRepository {

    /**
     * Create a new history entry for a status change
     */
    fun create(
        submissionId: Int,
        oldStatus: SubmissionStatus,
        newStatus: SubmissionStatus,
        changedBy: Int,
        userType: String,
        comment: String? = null
    ): SubmissionHistory {
        val submission = Submission.findById(submissionId)
            ?: throw IllegalArgumentException("Submission with id $submissionId not found")

        val now = Clock.System.now()
        return SubmissionHistory.new {
            this.submission = submission
            this.oldStatus = oldStatus.name
            this.newStatus = newStatus.name
            this.changedBy = changedBy
            this.userType = userType
            this.comment = comment
            this.createdAt = now
        }
    }

    /**
     * Find all history entries for a submission
     */
    fun findBySubmission(submissionId: Int): List<SubmissionHistory> {
        return SubmissionHistory.find { SubmissionHistoryTable.submission eq submissionId }
            .orderBy(SubmissionHistoryTable.createdAt to SortOrder.ASC)
            .toList()
    }

    /**
     * Find history entries by user
     */
    fun findByUser(userId: Int, userType: String): List<SubmissionHistory> {
        return SubmissionHistory.find {
            (SubmissionHistoryTable.changedBy eq userId) and
            (SubmissionHistoryTable.userType eq userType)
        }
            .orderBy(SubmissionHistoryTable.createdAt to SortOrder.DESC)
            .toList()
    }

    /**
     * Find all history entries
     */
    fun findAll(): List<SubmissionHistory> {
        return SubmissionHistory.all()
            .orderBy(SubmissionHistoryTable.createdAt to SortOrder.DESC)
            .toList()
    }

    /**
     * Find history entry by ID
     */
    fun findById(id: Int): SubmissionHistory? {
        return SubmissionHistory.findById(id)
    }

    /**
     * Delete history entry
     */
    fun delete(id: Int) {
        val history = SubmissionHistory.findById(id)
            ?: throw IllegalArgumentException("History entry with id $id not found")
        history.delete()
    }
}
