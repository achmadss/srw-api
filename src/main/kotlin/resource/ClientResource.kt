package resource

import JwtAuth
import com.srw.util.injectLazy
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.ByteArrayInputStream
import model.request.SetAddressRequest
import model.response.base.BaseResponse
import service.ClientService
import service.ImageUploadData
import service.PointService
import service.SubmissionService

@Resource("/clients")
class ClientResource {
    @Resource("submissions")
    class Submissions(
        val parent: ClientResource = ClientResource(),
        val page: Int = 1,
        val pageSize: Int = 20,
        val status: String? = null,
    ) {
        @Resource("new")
        class New(val parent: Submissions = Submissions())

        @Resource("{id}")
        class ById(val parent: Submissions = Submissions(), val id: Int)
    }
    @Resource("profile")
    class Profile(val parent: ClientResource = ClientResource()) {
        @Resource("points")
        class Points(
            val parent: Profile = Profile(),
            val page: Int = 1,
            val pageSize: Int = 20
        )
        @Resource("address")
        class Address(val parent: Profile = Profile())
    }
}

fun Route.clientResources() {
    val clientService by injectLazy<ClientService>()
    val pointService by injectLazy<PointService>()
    val submissionService by injectLazy<SubmissionService>()
    authenticate(JwtAuth.CLIENT) {
        get<ClientResource.Profile> {
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()
            val (code, response) = clientService.getById(clientId)
            call.respond(code, response)
        }

        get<ClientResource.Profile.Points> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()
            val (code, response) = pointService.getPointLedgerByClientId(clientId, resource.page, resource.pageSize)
            call.respond(code, response)
        }

        post<ClientResource.Profile.Address> {
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()
            val request = call.receive<SetAddressRequest>()
            val (code, response) = clientService.setAddress(
                id = clientId,
                address = request.address,
                latitude = request.latitude?.toFloat(),
                longitude = request.longitude?.toFloat()
            )
            call.respond(code, response)
        }

        // Create new submission with images (multipart/form-data)
        post<ClientResource.Submissions.New> {
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()

            val images = mutableListOf<ImageUploadData>()
            val multipart = call.receiveMultipart()

            // PNG file signature (first 8 bytes)
            val pngHeader = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
            )

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName ?: "unknown"

                        // Read all file bytes once
                        val bytes = part.provider().toInputStream().readBytes()
                        val size = bytes.size.toLong()

                        // Validate header: must match real PNG signature
                        val header = bytes.take(8).toByteArray()
                        val isPng = header.contentEquals(pngHeader)

                        if (!isPng) {
                            part.dispose()
                            return@forEachPart
                        }

                        images.add(
                            ImageUploadData(
                                inputStream = bytes.inputStream(),
                                fileName = fileName,
                                contentType = "image/png",
                                size = size
                            )
                        )

                        part.dispose()
                    }

                    else -> part.dispose()
                }
            }

            if (images.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    BaseResponse(
                        success = false,
                        code = 400,
                        message = "At least one valid PNG image is required",
                        data = null
                    )
                )
                return@post
            }

            val (code, response) = submissionService.createWithImages(
                clientId = clientId,
                images = images
            )
            call.respond(code, response)
        }

        // Get client's submissions with pagination
        get<ClientResource.Submissions> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()
            val (code, response) = submissionService.getByClientPaginated(
                clientId = clientId,
                page = resource.page,
                pageSize = resource.pageSize
            )
            call.respond(code, response)
        }

        // Get specific submission if it belongs to the authenticated client
        get<ClientResource.Submissions.ById> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()

            // Get submission
            val (code, response) = submissionService.getById(resource.id)

            // Check if submission belongs to the client
            if (code == HttpStatusCode.OK) {
                val submission = response.data
                if (submission != null && submission.clientId != clientId) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        BaseResponse(
                            success = false,
                            code = HttpStatusCode.Forbidden.value,
                            message = "You can only view your own submissions",
                            data = null
                        )
                    )
                    return@get
                }
            }

            call.respond(code, response)
        }
    }
}