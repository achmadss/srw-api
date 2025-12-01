import config.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.resources.*
import resource.configureResources

fun main(args: Array<String>) {
    validateEnvironmentVariables()
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { module() }
    ).start(wait = true)
}

fun Application.module() {
    configureHttp()
    configureKoin()
    configureSerialization()
    configureRequestValidation()
    configureSecurity()
    configureSchema()
    configureRabbitMQ()
    install(Resources)
    configureResources()
}