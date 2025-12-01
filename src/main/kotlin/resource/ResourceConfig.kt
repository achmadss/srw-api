package resource

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

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
