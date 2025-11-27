import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.resources.Resources
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