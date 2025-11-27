package module.v1.model

import kotlinx.serialization.Serializable

/**
 * Status of a submission in the workflow
 */
@Serializable
enum class SubmissionStatus {
    /**
     * Client has uploaded images and created the submission
     */
    PENDING,

    /**
     * ML program is currently processing the images
     */
    ML_PROCESSING,

    /**
     * ML processing is complete, waiting for admin review
     */
    AWAITING_REVIEW,

    /**
     * Admin has approved the submission
     */
    APPROVED,

    /**
     * Admin has rejected the submission (terminal state)
     */
    REJECTED,

    /**
     * Admin has assigned an agent to pickup the trash
     */
    ASSIGNED,

    /**
     * Agent has confirmed pickup of the trash
     */
    PICKED_UP,

    /**
     * Submission workflow is complete (terminal state)
     */
    COMPLETED;

    /**
     * Check if this status is a terminal state (no further transitions possible)
     */
    fun isTerminal(): Boolean = this == REJECTED || this == COMPLETED

    /**
     * Get valid next states from current status
     */
    fun validNextStates(): List<SubmissionStatus> = when (this) {
        PENDING -> listOf(ML_PROCESSING)
        ML_PROCESSING -> listOf(AWAITING_REVIEW)
        AWAITING_REVIEW -> listOf(APPROVED, REJECTED)
        APPROVED -> listOf(ASSIGNED)
        REJECTED -> emptyList() // Terminal state
        ASSIGNED -> listOf(PICKED_UP)
        PICKED_UP -> listOf(COMPLETED)
        COMPLETED -> emptyList() // Terminal state
    }

    /**
     * Check if transition to another status is valid
     */
    fun canTransitionTo(newStatus: SubmissionStatus): Boolean {
        return validNextStates().contains(newStatus)
    }
}
