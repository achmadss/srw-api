package resource

import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import resource.agent.agentResources
import resource.auth.authResources
import resource.client.clientResources
import resource.submission.submissionResources
import resource.trash.trashResources

fun Application.configureResources() {
    routing {
        // Health check endpoint
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "healthy"))
        }

        // Swagger UI
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        authResources()
        clientResources()
        agentResources()
        trashResources()
        submissionResources()
    }
}
