package service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import model.MLStatus
import model.SubmissionStatus
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.*
import util.MLAckMessage
import util.MLResultMessage
import util.RabbitMQClient
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MachineLearningService(
    private val rabbitMQClient: RabbitMQClient,
    private val submissionRepository: SubmissionRepository,
    private val submissionHistoryRepository: SubmissionHistoryRepository,
    private val imageRepository: ImageRepository,
    private val metadataRepository: MetadataRepository,
    private val trashRepository: TrashRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        // Start ML results consumer
        scope.launch {
            println("Starting ML results consumer...")
            rabbitMQClient.consumeMLResults { result ->
                handleMLResult(result)
            }
        }

        // Start ML ack consumer
        scope.launch {
            println("Starting ML ACK consumer...")
            rabbitMQClient.consumeMLAcks { ack ->
                println("Received ACK for submission ${ack.submissionId}")
                handleMLAck(ack)
            }
        }
    }

    private fun handleMLResult(result: MLResultMessage) {
        transaction {
            try {
                val submission = submissionRepository.findById(result.submissionId)
                if (submission == null || submission.getStatus() != SubmissionStatus.ML_PROCESSING) {
                    return@transaction
                }

                // Process each image result
                result.results.forEach { imageResult ->
                    if (imageResult.success) {
                        // Create metadata records
                        imageResult.trash.forEach { trashItem ->
                            val trash = trashRepository.findByName(trashItem.type)
                            if (trash != null) {
                                metadataRepository.create(
                                    amount = trashItem.amount,
                                    imageId = imageResult.imageId,
                                    trashName = trashItem.type
                                )
                            }
                        }

                        // Mark image as completed
                        imageRepository.updateMLStatus(
                            imageResult.imageId,
                            MLStatus.COMPLETED,
                            null
                        )
                    } else {
                        // Mark image as failed
                        imageRepository.updateMLStatus(
                            imageResult.imageId,
                            MLStatus.FAILED,
                            imageResult.error ?: "ML processing failed"
                        )
                    }
                }

                // Update submission status to AWAITING_REVIEW only if currently ML_PROCESSING
                if (submission.getStatus() == SubmissionStatus.ML_PROCESSING) {
                    val oldStatus = submission.getStatus()
                    val now = kotlin.time.Clock.System.now()
                    submissionRepository.updateStatus(
                        result.submissionId,
                        SubmissionStatus.AWAITING_REVIEW,
                        now
                    )

                    // Create history entry
                    submissionHistoryRepository.create(
                        submissionId = result.submissionId,
                        oldStatus = oldStatus,
                        newStatus = SubmissionStatus.AWAITING_REVIEW,
                        changedBy = 0, // System user
                        userType = "system",
                        comment = "ML processing completed"
                    )
                }

                println("Successfully processed ML results for submission ${result.submissionId}")

            } catch (e: Exception) {
                println("Error handling ML result for submission ${result.submissionId}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun handleMLAck(ack: MLAckMessage) {
        transaction {
            try {
                // Update submission status to ML_PROCESSING when ack is received
                val submission = submissionRepository.findById(ack.submissionId)
                if (submission != null && submission.getStatus() == SubmissionStatus.PENDING) {
                    val oldStatus = submission.getStatus()
                    val now = kotlin.time.Clock.System.now()
                    submissionRepository.updateStatus(
                        ack.submissionId,
                        SubmissionStatus.ML_PROCESSING,
                        now
                    )

                    // Create history entry
                    submissionHistoryRepository.create(
                        submissionId = ack.submissionId,
                        oldStatus = oldStatus,
                        newStatus = SubmissionStatus.ML_PROCESSING,
                        changedBy = 0, // System user
                        userType = "system",
                        comment = "ML processing started"
                    )

                    println("Successfully updated submission ${ack.submissionId} status to ML_PROCESSING")
                }
            } catch (e: Exception) {
                println("Error handling ML ack for submission ${ack.submissionId}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
