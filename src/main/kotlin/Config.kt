import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.srw.util.inject
import module.model.AdminTable
import module.model.AgentTable
import module.model.ClientTable
import module.model.ImageTable
import module.model.MetadataTable
import module.model.PointTable
import module.model.SubmissionTable
import module.model.SubmissionHistoryTable
import module.model.TrashTable
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import resource.auth.BasicAuthRequest
import resource.auth.NfcAuthRequest
import resource.auth.RefreshTokenAuthRequest
import resource.client.ClientResource
import resource.client.CreateClientRequest
import module.model.RefreshTokenTable
import module.repository.AdminRepository
import module.repository.AgentRepository
import module.repository.ClientRepository
import module.repository.ImageRepository
import module.repository.MetadataRepository
import module.repository.PointRepository
import module.repository.RefreshTokenRepository
import module.repository.SubmissionRepository
import module.repository.SubmissionHistoryRepository
import module.repository.TrashRepository
import module.service.AgentService
import module.service.AuthService
import module.service.ClientService
import module.service.ImageService
import module.service.MachineLearningService
import module.service.SubmissionService
import module.service.TrashService
import module.service.MinIOStorageService
import util.RabbitMQClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

fun Application.configureHttp() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
            }
        )
    }
}

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        // Auth validations
        validate<BasicAuthRequest> { request ->
            when {
                request.username.isBlank() -> ValidationResult.Invalid("Username cannot be blank")
                request.password.isBlank() -> ValidationResult.Invalid("Password cannot be blank")
                else -> ValidationResult.Valid
            }
        }
        validate<NfcAuthRequest> { request ->
            when {
                request.nfc.isBlank() -> ValidationResult.Invalid("NFC cannot be blank")
                else -> ValidationResult.Valid
            }
        }
        validate<RefreshTokenAuthRequest> { request ->
            when {
                request.refreshToken.isBlank() -> ValidationResult.Invalid("Refresh token cannot be blank")
                else -> ValidationResult.Valid
            }
        }

        // Client validations
        validate<ClientResource> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                resource.pageSize > 100 -> ValidationResult.Invalid("Page size cannot exceed 100")
                else -> ValidationResult.Valid
            }
        }
        validate<ClientResource.GetByNFC> { resource ->
            when {
                resource.nfc.isBlank() -> ValidationResult.Invalid("NFC cannot be blank")
                else -> ValidationResult.Valid
            }
        }
        validate<CreateClientRequest> { request ->
            when {
                request.nfc.isBlank() -> ValidationResult.Invalid("NFC cannot be blank")
                request.name.isBlank() -> ValidationResult.Invalid("Name cannot be blank")
                request.address.isBlank() -> ValidationResult.Invalid("Address cannot be blank")
                else -> ValidationResult.Valid
            }
        }
    }
}

fun Application.configureRabbitMQ() {
    // Initialize RabbitMQ connection
    val rabbitMQClient = inject<RabbitMQClient>()
    rabbitMQClient.connect()

    // Start ML results consumer
    val machineLearningService = inject<module.service.MachineLearningService>()
    machineLearningService.start()

    println("✓ RabbitMQ initialized and ML results consumer started")
}

fun Application.configureSchema() {
    transaction {
        SchemaUtils.create(
            _root_ide_package_.module.model.AdminTable,
            _root_ide_package_.module.model.AgentTable,
            _root_ide_package_.module.model.ClientTable,
            _root_ide_package_.module.model.ImageTable,
            _root_ide_package_.module.model.MetadataTable,
            _root_ide_package_.module.model.PointTable,
            _root_ide_package_.module.model.SubmissionTable,
            _root_ide_package_.module.model.SubmissionHistoryTable,
            _root_ide_package_.module.model.RefreshTokenTable,
            _root_ide_package_.module.model.TrashTable,
        )
        inject<module.repository.AdminRepository>().seedDefaultAdmin()
        inject<module.repository.TrashRepository>().seedTrashTypesFromConfig()
    }
}

fun Application.configureSecurity() {
    val adminSecret = getRequiredEnv(Env.ADMIN_JWT_SECRET)
    val clientSecret = getRequiredEnv(Env.CLIENT_JWT_SECRET)
    val agentSecret = getRequiredEnv(Env.AGENT_JWT_SECRET)
    val issuer = getRequiredEnv(Env.JWT_ISSUER)
    val adminAudience = getRequiredEnv(Env.ADMIN_JWT_AUDIENCE)
    val clientAudience = getRequiredEnv(Env.CLIENT_JWT_AUDIENCE)
    val agentAudience = getRequiredEnv(Env.AGENT_JWT_AUDIENCE)
    val realm = "SRW API"

    install(Authentication) {
        // Admin authentication - can access admin, client, and agent resources
        jwt(JwtAuth.ADMIN) {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(adminSecret))
                    .withAudience(adminAudience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null &&
                    credential.payload.getClaim("userType").asString() == "admin"
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }

        // Client authentication - can only access client resources
        jwt(JwtAuth.CLIENT) {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(clientSecret))
                    .withAudience(clientAudience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null &&
                    credential.payload.getClaim("userType").asString() == "client"
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }

        // Agent authentication - can only access agent resources
        jwt(JwtAuth.AGENT) {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(agentSecret))
                    .withAudience(agentAudience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null &&
                    credential.payload.getClaim("userType").asString() == "agent"
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
        }
    }
}

fun Application.configureKoin() {
    install(Koin) {
        modules(
            module {
                single<Database>(createdAtStart = true) {
                    Database.connect(
                        url = getRequiredEnv(Env.DB_URL),
                        driver = "org.postgresql.Driver",
                        user = getRequiredEnv(Env.DB_USER),
                        password = getRequiredEnv(Env.DB_PASSWORD),
                    )
                }
                // Repositories
                single<module.repository.RefreshTokenRepository> { _root_ide_package_.module.repository.RefreshTokenRepository() }
                single<module.repository.AdminRepository> { _root_ide_package_.module.repository.AdminRepository() }
                single<module.repository.AgentRepository> { _root_ide_package_.module.repository.AgentRepository() }
                single<module.repository.ClientRepository> { _root_ide_package_.module.repository.ClientRepository() }
                single<module.repository.TrashRepository> { _root_ide_package_.module.repository.TrashRepository() }
                single<module.repository.SubmissionRepository> { _root_ide_package_.module.repository.SubmissionRepository() }
                single<module.repository.SubmissionHistoryRepository> { _root_ide_package_.module.repository.SubmissionHistoryRepository() }
                single<module.repository.ImageRepository> { _root_ide_package_.module.repository.ImageRepository() }
                single<module.repository.MetadataRepository> { _root_ide_package_.module.repository.MetadataRepository() }
                single<module.repository.PointRepository> { _root_ide_package_.module.repository.PointRepository() }

                // Storage
                single<module.service.MinIOStorageService>(createdAtStart = true) {
                    _root_ide_package_.module.service.MinIOStorageService(
                        endpoint = getRequiredEnv(Env.MINIO_ENDPOINT),
                        accessKey = getRequiredEnv(Env.MINIO_ACCESS_KEY),
                        secretKey = getRequiredEnv(Env.MINIO_SECRET_KEY),
                        bucketName = getRequiredEnv(Env.MINIO_BUCKET)
                    )
                }

                // RabbitMQ
                single<RabbitMQClient>(createdAtStart = true) { RabbitMQClient() }

                // Services
                single<module.service.AuthService> {
                    _root_ide_package_.module.service.AuthService(
                        adminRepository = get(),
                        clientRepository = get(),
                        agentRepository = get(),
                        refreshTokenRepository = get()
                    )
                }
                single<module.service.ImageService> {
                    _root_ide_package_.module.service.ImageService(
                        imageRepository = get(),
                        storageService = get()
                    )
                }
                single<module.service.ClientService> {
                    _root_ide_package_.module.service.ClientService(
                        clientRepository = get(),
                        pointRepository = get()
                    )
                }
                single<module.service.AgentService> {
                    _root_ide_package_.module.service.AgentService(
                        agentRepository = get()
                    )
                }
                single<module.service.TrashService> {
                    _root_ide_package_.module.service.TrashService(
                        trashRepository = get()
                    )
                }
                single<module.service.SubmissionService> {
                    _root_ide_package_.module.service.SubmissionService(
                        submissionRepository = get(),
                        submissionHistoryRepository = get(),
                        imageRepository = get(),
                        metadataRepository = get(),
                        trashRepository = get(),
                        pointRepository = get(),
                        imageService = get(),
                        rabbitMQClient = get()
                    )
                }
                single<module.service.MachineLearningService> {
                    _root_ide_package_.module.service.MachineLearningService(
                        rabbitMQClient = get(),
                        submissionRepository = get(),
                        submissionHistoryRepository = get(),
                        imageRepository = get(),
                        metadataRepository = get(),
                        trashRepository = get()
                    )
                }
            }
        )
    }
}
