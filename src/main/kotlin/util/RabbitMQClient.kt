package util

import Env
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import getRequiredEnv
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MLJobMessage(
    val submissionId: Int,
    val images: List<ImageMessage>
)

@Serializable
data class ImageMessage(
    val id: String
)

@Serializable
data class MLResultMessage(
    val submissionId: Int,
    val results: List<MLImageResult>
)

@Serializable
data class MLImageResult(
    val imageId: String,
    val success: Boolean,
    val trash: List<MLTrashItem> = emptyList(),
    val error: String? = null
)

@Serializable
data class MLTrashItem(
    val type: String,
    val amount: Int
)

@Serializable
data class MLAckMessage(
    val submissionId: Int
)

class RabbitMQClient {
    private val factory = ConnectionFactory().apply {
        setUri(getRequiredEnv(Env.RABBITMQ_URL))
    }

    private var connection: Connection? = null
    private var channel: Channel? = null
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val ML_QUEUE_NAME = "ml_processing_queue"
        const val ML_RESULTS_QUEUE_NAME = "ml_results_queue"
        const val ML_JOB_ACK_QUEUE_NAME = "ml_job_ack_queue"
    }

    fun connect() {
        connection = factory.newConnection()
        channel = connection?.createChannel()

        // Declare queues (durable = true for persistence)
        channel?.queueDeclare(ML_QUEUE_NAME, true, false, false, null)
        channel?.queueDeclare(ML_RESULTS_QUEUE_NAME, true, false, false, null)
        channel?.queueDeclare(ML_JOB_ACK_QUEUE_NAME, true, false, false, null)
    }

    fun publishMLJob(submissionId: Int, images: List<ImageMessage>) {
        val message = MLJobMessage(submissionId, images)
        val messageBody = json.encodeToString(message).toByteArray()

        channel?.basicPublish(
            "",
            ML_QUEUE_NAME,
            null,
            messageBody
        )
    }

    fun consumeMLResults(callback: (MLResultMessage) -> Unit) {
        val deliverCallback = DeliverCallback { _, delivery ->
            val message = String(delivery.body)
            val result = json.decodeFromString<MLResultMessage>(message)
            callback(result)
            channel?.basicAck(delivery.envelope.deliveryTag, false)
        }

        channel?.basicConsume(ML_RESULTS_QUEUE_NAME, false, deliverCallback) { _ -> }
    }

    fun consumeMLAcks(callback: (MLAckMessage) -> Unit) {
        val deliverCallback = DeliverCallback { _, delivery ->
            val message = String(delivery.body)
            val ack = json.decodeFromString<MLAckMessage>(message)
            callback(ack)
            channel?.basicAck(delivery.envelope.deliveryTag, false)
        }

        channel?.basicConsume(ML_JOB_ACK_QUEUE_NAME, false, deliverCallback) { _ -> }
    }

    fun close() {
        channel?.close()
        connection?.close()
    }
}
