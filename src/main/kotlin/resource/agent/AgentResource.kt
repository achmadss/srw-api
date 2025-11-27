package resource.agent

import JwtAuth
import com.srw.util.injectLazy
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import module.v1.service.AgentService
import module.v1.service.CreateAgentRequest
import module.v1.service.UpdateAgentRequest

/**
 * Type-safe agent resources
 */
@Resource("/agents")
class AgentResource(
    val page: Int = 1,
    val pageSize: Int = 20
) {
    @Resource("new")
    class New(val parent: AgentResource = AgentResource())

    @Resource("{id}")
    class ById(val parent: AgentResource = AgentResource(), val id: Int)
}

/**
 * Agent routes (Admin only)
 */
fun Route.agentResources() {
    val agentService by injectLazy<AgentService>()

    authenticate(JwtAuth.ADMIN) {
        // Get all agents (paginated)
        get<AgentResource> { resource ->
            val (code, response) = agentService.getPaginated(
                page = resource.page,
                pageSize = resource.pageSize
            )
            call.respond(code, response)
        }

        // Create new agent
        post<AgentResource.New> {
            val request = call.receive<CreateAgentRequest>()
            val (code, response) = agentService.create(
                name = request.name,
                username = request.username,
                password = request.password
            )
            call.respond(code, response)
        }

        // Get agent by ID
        get<AgentResource.ById> { resource ->
            val (code, response) = agentService.getById(resource.id)
            call.respond(code, response)
        }

        // Update agent
        put<AgentResource.ById> { resource ->
            val request = call.receive<UpdateAgentRequest>()
            val (code, response) = agentService.update(
                id = resource.id,
                name = request.name,
                username = request.username,
                password = request.password
            )
            call.respond(code, response)
        }

        // Delete agent
        delete<AgentResource.ById> { resource ->
            val (code, response) = agentService.delete(resource.id)
            call.respond(code, response)
        }
    }
}