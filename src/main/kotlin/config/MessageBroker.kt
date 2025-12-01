package config

import com.srw.util.inject
import io.ktor.server.application.Application
import service.MachineLearningService
import util.RabbitMQClient

fun Application.configureRabbitMQ() {
    // Initialize RabbitMQ connection
    val rabbitMQClient = inject<RabbitMQClient>()
    rabbitMQClient.connect()

    // Start ML results consumer
    val machineLearningService = inject<MachineLearningService>()
    machineLearningService.start()

    println("✓ RabbitMQ initialized and ML results consumer started")
}

