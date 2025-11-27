package resource.trash

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
import module.v1.service.CreateTrashRequest
import module.v1.service.TrashService
import module.v1.service.UpdateTrashRequest

/**
 * Type-safe trash resources
 */
@Resource("/trash")
class TrashResource {
    @Resource("new")
    class New(val parent: TrashResource = TrashResource())

    @Resource("{name}")
    class ByName(val parent: TrashResource = TrashResource(), val name: String)
}

/**
 * Trash type routes
 */
fun Route.trashResources() {
    val trashService by injectLazy<TrashService>()

    // Get all trash types (public or authenticated - can be accessed by all)
    get<TrashResource> {
        val (code, response) = trashService.getAll()
        call.respond(code, response)
    }

    // Get trash type by name (public or authenticated)
    get<TrashResource.ByName> { resource ->
        val (code, response) = trashService.getByName(resource.name)
        call.respond(code, response)
    }

    // Admin-only routes for managing trash types
    authenticate(JwtAuth.ADMIN) {
        // Create new trash type
        post<TrashResource.New> {
            val request = call.receive<CreateTrashRequest>()
            val (code, response) = trashService.create(
                name = request.name,
                pointsPerUnit = request.pointsPerUnit
            )
            call.respond(code, response)
        }

        // Update trash type (only pointsPerUnit can be updated, name is primary key)
        put<TrashResource.ByName> { resource ->
            val request = call.receive<UpdateTrashRequest>()
            val (code, response) = trashService.update(
                name = resource.name,
                pointsPerUnit = request.pointsPerUnit
            )
            call.respond(code, response)
        }

        // Delete trash type
        delete<TrashResource.ByName> { resource ->
            val (code, response) = trashService.delete(resource.name)
            call.respond(code, response)
        }
    }
}