package resource

import JwtAuth
import com.srw.util.injectLazy
import io.ktor.resources.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import model.SubmissionStatus
import model.request.*
import service.*

/**
 * Type-safe admin resources consolidating all admin-only endpoints
 */
@Resource("admin")
class AdminResource {
    @Resource("clients")
    class Clients(
        val parent: AdminResource = AdminResource(),
        val page: Int = 1,
        val pageSize: Int = 20
    ) {
        @Resource("new")
        class New(val parent: Clients = Clients())
    }

    @Resource("agents")
    class Agents(
        val parent: AdminResource = AdminResource(),
        val page: Int = 1,
        val pageSize: Int = 20
    ) {
        @Resource("new")
        class New(val parent: Agents = Agents())

        @Resource("{id}")
        class ById(val parent: Agents = Agents(), val id: Int)
    }

    @Resource("points")
    class Points(val parent: AdminResource = AdminResource()) {
        @Resource("{clientId}")
        class ClientPoints(
            val parent: Points = Points(),
            val clientId: Int,
            val page: Int = 1,
            val pageSize: Int = 20
        ) {
            @Resource("claim")
            class Claim(val parent: ClientPoints)
        }
    }

    @Resource("submissions")
    class Submissions(
        val parent: AdminResource = AdminResource(),
        val page: Int = 1,
        val pageSize: Int = 20,
        val status: String? = null
    ) {
        @Resource("{id}")
        class ById(val parent: Submissions = Submissions(), val id: Int) {
            @Resource("review")
            class Review(val parent: ById)

            @Resource("assign")
            class Assign(val parent: ById)

            @Resource("history")
            class History(val parent: ById)

            @Resource("ml-status")
            class MLStatus(val parent: ById)

            @Resource("images")
            class Images(val parent: ById) {
                @Resource("{imageId}")
                class ImageId(val parent: Images, val imageId: String) {
                    @Resource("metadata")
                    class Metadata(val parent: ImageId)
                }
            }
        }
    }

    @Resource("trash")
    class Trash(val parent: AdminResource = AdminResource()) {
        @Resource("new")
        class New(val parent: Trash = Trash())

        @Resource("{name}")
        class ByName(val parent: Trash = Trash(), val name: String)
    }
}

/**
 * Admin routes consolidating all admin-only endpoints
 */
fun Route.adminResources() {
    val clientService by injectLazy<ClientService>()
    val agentService by injectLazy<AgentService>()
    val pointService by injectLazy<PointService>()
    val submissionService by injectLazy<SubmissionService>()
    val trashService by injectLazy<TrashService>()

    authenticate(JwtAuth.ADMIN) {
        // Client management
        get<AdminResource.Clients> { resource ->
            val (code, response) = clientService.getPaginated(
                page = resource.page,
                pageSize = resource.pageSize
            )
            call.respond(code, response)
        }
        post<AdminResource.Clients.New> {
            val body = call.receive<CreateClientRequest>()
            val (code, response) = clientService.create(
                name = body.name,
                nfc = body.nfc,
                address = body.address
            )
            call.respond(code, response)
        }

        // Agent management
        get<AdminResource.Agents> { resource ->
            val (code, response) = agentService.getPaginated(
                page = resource.page,
                pageSize = resource.pageSize
            )
            call.respond(code, response)
        }
        post<AdminResource.Agents.New> {
            val request = call.receive<CreateAgentRequest>()
            val (code, response) = agentService.create(
                name = request.name,
                username = request.username,
                password = request.password
            )
            call.respond(code, response)
        }
        get<AdminResource.Agents.ById> { resource ->
            val (code, response) = agentService.getById(resource.id)
            call.respond(code, response)
        }
        put<AdminResource.Agents.ById> { resource ->
            val request = call.receive<UpdateAgentRequest>()
            val (code, response) = agentService.update(
                id = resource.id,
                name = request.name,
                username = request.username,
                password = request.password
            )
            call.respond(code, response)
        }
        delete<AdminResource.Agents.ById> { resource ->
            val (code, response) = agentService.delete(resource.id)
            call.respond(code, response)
        }

        // Point management
        get<AdminResource.Points.ClientPoints> { resource ->
            val (code, response) = pointService.getPointLedgerByClientId(resource.clientId, resource.page, resource.pageSize)
            call.respond(code, response)
        }
        post<AdminResource.Points.ClientPoints.Claim> { resource ->
            val body = call.receive<ClaimPointsRequest>()
            val (code, response) = pointService.claimPointsByClientId(resource.parent.clientId, body.amount)
            call.respond(code, response)
        }

        // Submission management
        get<AdminResource.Submissions> { resource ->
            val status = resource.status?.let { SubmissionStatus.valueOf(it) }
            val (code, response) = submissionService.getPaginated(
                page = resource.page,
                pageSize = resource.pageSize,
                status = status
            )
            call.respond(code, response)
        }
        get<AdminResource.Submissions.ById> { resource ->
            val (code, response) = submissionService.getById(resource.id)
            call.respond(code, response)
        }
        post<AdminResource.Submissions.ById.Review> { resource ->
            val request = call.receive<ReviewSubmissionRequest>()
            // Note: Would need adminId from auth, but no auth yet
            val (code, response) = submissionService.review(
                id = resource.parent.id,
                adminId = 1, // placeholder
                approved = request.approved,
                rejectionReason = request.rejectionReason,
                adminNotes = request.adminNotes
            )
            call.respond(code, response)
        }
        post<AdminResource.Submissions.ById.Assign> { resource ->
            val request = call.receive<AssignAgentRequest>()
            // Note: Would need adminId from auth
            val (code, response) = submissionService.assignAgent(
                id = resource.parent.id,
                adminId = 1, // placeholder
                agentId = request.agentId
            )
            call.respond(code, response)
        }
        get<AdminResource.Submissions.ById.History> { resource ->
            val (code, response) = submissionService.getHistory(resource.parent.id)
            call.respond(code, response)
        }
        get<AdminResource.Submissions.ById.MLStatus> { resource ->
            val (code, response) = submissionService.getMLStatus(resource.parent.id)
            call.respond(code, response)
        }
        post<AdminResource.Submissions.ById.Images.ImageId.Metadata> { resource ->
            val request = call.receive<ManualMetadataRequest>()
            val (code, response) = submissionService.updateMetadata(
                submissionId = resource.parent.parent.parent.id,
                imageId = resource.parent.imageId,
                metadata = request.metadata
            )
            call.respond(code, response)
        }

        // Trash management
        get<AdminResource.Trash> {
            val (code, response) = trashService.getAll()
            call.respond(code, response)
        }
        get<AdminResource.Trash.ByName> { resource ->
            val (code, response) = trashService.getByName(resource.name)
            call.respond(code, response)
        }
        post<AdminResource.Trash.New> {
            val request = call.receive<CreateTrashRequest>()
            val (code, response) = trashService.create(
                name = request.name,
                pointsPerUnit = request.pointsPerUnit
            )
            call.respond(code, response)
        }
        put<AdminResource.Trash.ByName> { resource ->
            val request = call.receive<UpdateTrashRequest>()
            val (code, response) = trashService.update(
                name = resource.name,
                pointsPerUnit = request.pointsPerUnit
            )
            call.respond(code, response)
        }
        delete<AdminResource.Trash.ByName> { resource ->
            val (code, response) = trashService.delete(resource.name)
            call.respond(code, response)
        }
    }
}