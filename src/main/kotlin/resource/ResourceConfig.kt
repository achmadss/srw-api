package resource

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureResources() {
    routing {
        // Health check endpoint
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        // Swagger UI
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        authResources()
        adminResources()
        clientResources()
        agentResources()
    }
}
