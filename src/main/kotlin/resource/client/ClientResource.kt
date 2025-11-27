package resource.client

import JwtAuth
import com.srw.util.injectLazy
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import module.v1.service.ClientService
import kotlin.getValue

@Resource("/clients")
class ClientResource(
    val page: Int = 1,
    val pageSize: Int = 20,
) {
    @Resource("new")
    class New(val parent: ClientResource = ClientResource())

    @Resource("{nfc}")
    class GetByNFC(
        val parent: ClientResource = ClientResource(),
        val nfc: String,
    )
}

fun Route.clientResources() {
    val clientService by injectLazy<ClientService>()
    authenticate(JwtAuth.ADMIN) {
        get<ClientResource> { resource ->
            val (code, response) = clientService.getPaginated(
                page = resource.page,
                pageSize = resource.pageSize
            )
            call.respond(code, response)
        }
        post<ClientResource.New> {
            val body = call.receive<CreateClientRequest>()
            val (code, response) = clientService.create(
                name = body.name,
                nfc = body.nfc,
                address = body.address
            )
            call.respond(code, response)
        }
    }
    authenticate(JwtAuth.ADMIN, JwtAuth.CLIENT) {
        get<ClientResource.GetByNFC> { resource ->
            val (code, response) = clientService.getByNfc(resource.nfc)
            call.respond(code, response)
        }
    }
}