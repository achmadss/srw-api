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
import model.response.base.BaseResponse
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

        @Resource("points")
        class Points(
            val parent: Submissions = Submissions(),
            val page: Int = 1,
            val pageSize: Int = 20
        )
    }
}

fun Route.clientResources() {
    val pointService by injectLazy<PointService>()
    val submissionService by injectLazy<SubmissionService>()
    authenticate(JwtAuth.CLIENT) {
        get<ClientResource.Submissions.Points> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()
            val (code, response) = pointService.getPointLedgerByClientId(clientId, resource.page, resource.pageSize)
            call.respond(code, response)
        }

        // Create new submission with images (multipart/form-data)
        post<ClientResource.Submissions.New> {
            val principal = call.principal<JWTPrincipal>()!!
            val clientId = principal.payload.getClaim("userId").asInt()

            // Collect image uploads from multipart data
            val images = mutableListOf<ImageUploadData>()
            val multipart = call.receiveMultipart()

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName ?: "unknown"
                        val contentType = part.contentType?.toString() ?: "application/octet-stream"

                        // Get input stream and size
                        val inputStream = part.streamProvider()
                        val bytes = inputStream.readBytes()
                        val size = bytes.size.toLong()

                        images.add(
                            ImageUploadData(
                                inputStream = bytes.inputStream(),
                                fileName = fileName,
                                contentType = contentType,
                                size = size
                            )
                        )
                    }
                    else -> part.dispose()
                }
            }

            // Validate that at least one image was uploaded
            if (images.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    BaseResponse(
                        success = false,
                        code = 400,
                        message = "At least one image is required",
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