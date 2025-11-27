package resource.submission

import JwtAuth
import UserType
import com.srw.util.injectLazy
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import module.v1.model.SubmissionStatus
import module.v1.model.response.BaseResponse
import module.v1.service.ImageUploadData
import module.v1.service.SubmissionService

/**
 * Type-safe submission resources
 */
@Resource("/submissions")
class SubmissionResource(
    val page: Int = 1,
    val pageSize: Int = 20,
    val status: String? = null // Optional status filter
) {
    @Resource("new")
    class New(val parent: SubmissionResource = SubmissionResource())

    @Resource("{id}")
    class ById(val parent: SubmissionResource = SubmissionResource(), val id: Int) {
        @Resource("review")
        class Review(val parent: ById)

        @Resource("assign")
        class Assign(val parent: ById)

        @Resource("pickup")
        class Pickup(val parent: ById)

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

/**
 * Submission routes
 */
fun Route.submissionResources() {
    val submissionService by injectLazy<SubmissionService>()

    // ==================== Client + Admin Routes ====================
    authenticate(JwtAuth.CLIENT, JwtAuth.ADMIN) {
        // Create new submission with images (multipart/form-data)
        post<SubmissionResource.New> {
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
    }

    // ==================== Agent + Admin Routes ====================
    authenticate(JwtAuth.ADMIN, JwtAuth.AGENT) {
        // Confirm pickup
        post<SubmissionResource.ById.Pickup> { resource ->
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

    // ==================== Admin-Only Routes ====================
    authenticate(JwtAuth.ADMIN) {
        // Review submission (approve/reject)
        post<SubmissionResource.ById.Review> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val adminId = principal.payload.getClaim("userId").asInt()

            val request = call.receive<ReviewSubmissionRequest>()
            val (code, response) = submissionService.review(
                id = resource.parent.id,
                adminId = adminId,
                approved = request.approved,
                rejectionReason = request.rejectionReason,
                adminNotes = request.adminNotes
            )
            call.respond(code, response)
        }

        // Assign agent to submission
        post<SubmissionResource.ById.Assign> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val adminId = principal.payload.getClaim("userId").asInt()

            val request = call.receive<AssignAgentRequest>()
            val (code, response) = submissionService.assignAgent(
                id = resource.parent.id,
                adminId = adminId,
                agentId = request.agentId
            )
            call.respond(code, response)
        }

        // Get submission history (admin only)
        get<SubmissionResource.ById.History> { resource ->
            val (code, response) = submissionService.getHistory(resource.parent.id)
            call.respond(code, response)
        }

        // Get ML processing status (admin only)
        get<SubmissionResource.ById.MLStatus> { resource ->
            val (code, response) = submissionService.getMLStatus(resource.parent.id)
            call.respond(code, response)
        }

        // Update metadata for an image (replaces existing metadata)
        post<SubmissionResource.ById.Images.ImageId.Metadata> { resource ->
            val request = call.receive<ManualMetadataRequest>()
            val (code, response) = submissionService.updateMetadata(
                submissionId = resource.parent.parent.parent.id,
                imageId = resource.parent.imageId,
                metadata = request.metadata
            )
            call.respond(code, response)
        }
    }

    // ==================== Shared Routes (Client + Admin + Agent) ====================
    // These endpoints can be accessed by multiple user types with role-based authorization
    authenticate(JwtAuth.CLIENT, JwtAuth.ADMIN, JwtAuth.AGENT) {
        // Get submissions (behavior depends on user type)
        get<SubmissionResource> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("userId").asInt()
            val userType = UserType(principal.payload.getClaim("userType").asString())

            if (userType == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    BaseResponse(
                        success = false,
                        code = HttpStatusCode.Unauthorized.value,
                        message = "Invalid user type",
                        data = null
                    )
                )
                return@get
            }

            val status = resource.status?.let { SubmissionStatus.valueOf(it) }

            val (code, response) = when (userType) {
                UserType.ADMIN -> {
                    // Admin can get all submissions (paginated)
                    submissionService.getPaginated(
                        page = resource.page,
                        pageSize = resource.pageSize,
                        status = status
                    )
                }
                UserType.CLIENT -> {
                    // Client can only get own submissions
                    submissionService.getByClientPaginated(userId, resource.page, resource.pageSize, status)
                }
                UserType.AGENT -> {
                    // Agent can only get assigned submissions
                    submissionService.getByAgentPaginated(userId, resource.page, resource.pageSize, status)
                }
            }

            call.respond(code, response)
        }

        // Get submission details
        get<SubmissionResource.ById> { resource ->
            val principal = call.principal<JWTPrincipal>()!!
            val userId = principal.payload.getClaim("userId").asInt()
            val userType = UserType(principal.payload.getClaim("userType").asString())

            if (userType == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    BaseResponse(
                        success = false,
                        code = HttpStatusCode.Unauthorized.value,
                        message = "Invalid user type",
                        data = null
                    )
                )
                return@get
            }

            // Get submission
            val (code, response) = submissionService.getById(resource.id)

            // Check authorization based on user type
            if (code == HttpStatusCode.OK) {
                val submission = response.data
                when (userType) {
                    UserType.ADMIN -> {
                        // Admin can view any submission - no check needed
                    }
                    UserType.CLIENT -> {
                        // Client can only view own submissions
                        if (submission != null && submission.clientId != userId) {
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
                    UserType.AGENT -> {
                        // Agent can only view assigned submissions
                        if (submission != null && submission.agentId != userId) {
                            call.respond(
                                HttpStatusCode.Forbidden,
                                BaseResponse(
                                    success = false,
                                    code = HttpStatusCode.Forbidden.value,
                                    message = "You can only view submissions assigned to you",
                                    data = null
                                )
                            )
                            return@get
                        }
                    }
                }
            }

            call.respond(code, response)
        }
    }
}