package resource

import JwtAuth
import com.srw.util.injectLazy
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import model.response.base.BaseResponse
import service.SubmissionService
import model.request.ConfirmPickupRequest

/**
 * Type-safe agent resources
 */
@Resource("/agents")
class AgentResource(
    val page: Int = 1,
    val pageSize: Int = 20
) {
    @Resource("submissions")
    class Submissions(val parent: AgentResource = AgentResource(), val page: Int = 1, val pageSize: Int = 20) {
        @Resource("{id}")
        class ById(val parent: Submissions = Submissions(), val id: Int) {
            @Resource("pickup")
            class Pickup(val parent: ById)
        }
    }
}

/**
 * Agent routes (Admin only)
 */
fun Route.agentResources() {
    val submissionService by injectLazy<SubmissionService>()

    authenticate(JwtAuth.AGENT) {
        // Get agent's assigned submissions (paginated)
        get<AgentResource.Submissions> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val agentId = principal.payload.getClaim("userId").asInt()
            val (code, response) = submissionService.getByAgentPaginated(
                agentId = agentId,
                page = resource.page,
                pageSize = resource.pageSize
            )
            call.respond(code, response)
        }

        // Get specific submission if assigned to agent
        get<AgentResource.Submissions.ById> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val agentId = principal.payload.getClaim("userId").asInt()
            val (code, response) = submissionService.getById(resource.id)
            // Check if submission is assigned to this agent
            if (code == HttpStatusCode.OK) {
                val submission = response.data
                if (submission?.agentId != agentId) {
                    call.respond(
                        HttpStatusCode.Forbidden, BaseResponse(
                        success = false,
                        code = HttpStatusCode.Forbidden.value,
                        message = "Access denied: submission not assigned to you",
                        data = null
                    ))
                    return@get
                }
            }
            call.respond(code, response)
        }

        // Confirm pickup of assigned submission
        post<AgentResource.Submissions.ById.Pickup> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val agentId = principal.payload.getClaim("userId").asInt()
            val request = call.receive<ConfirmPickupRequest>()
            val (code, response) = submissionService.confirmPickup(
                id = resource.parent.id,
                agentId = agentId,
                notes = request.notes
            )
            call.respond(code, response)
        }
    }
}