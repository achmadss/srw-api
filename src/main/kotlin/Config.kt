import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.srw.util.inject
import module.v1.model.AdminTable
import module.v1.model.AgentTable
import module.v1.model.ClientTable
import module.v1.model.ImageTable
import module.v1.model.MetadataTable
import module.v1.model.PointTable
import module.v1.model.SubmissionTable
import module.v1.model.SubmissionHistoryTable
import module.v1.model.TrashTable
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
import module.v1.model.RefreshTokenTable
import module.v1.repository.AdminRepository
import module.v1.repository.AgentRepository
import module.v1.repository.ClientRepository
import module.v1.repository.ImageRepository
import module.v1.repository.MetadataRepository
import module.v1.repository.PointRepository
import module.v1.repository.RefreshTokenRepository
import module.v1.repository.SubmissionRepository
import module.v1.repository.SubmissionHistoryRepository
import module.v1.repository.TrashRepository
import module.v1.service.AgentService
import module.v1.service.AuthService
import module.v1.service.ClientService
import module.v1.service.ImageService
import module.v1.service.MachineLearningService
import module.v1.service.SubmissionService
import module.v1.service.TrashService
import module.v1.service.MinIOStorageService
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
    val machineLearningService = inject<MachineLearningService>()
    machineLearningService.start()

    println("✓ RabbitMQ initialized and ML results consumer started")
}

fun Application.configureSchema() {
    transaction {
        SchemaUtils.create(
            AdminTable,
            AgentTable,
            ClientTable,
            ImageTable,
            MetadataTable,
            PointTable,
            SubmissionTable,
            SubmissionHistoryTable,
            RefreshTokenTable,
            TrashTable,
        )
        inject<AdminRepository>().seedDefaultAdmin()
        inject<TrashRepository>().seedTrashTypesFromConfig()
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
                single<RefreshTokenRepository> { RefreshTokenRepository() }
                single<AdminRepository> { AdminRepository() }
                single<AgentRepository> { AgentRepository() }
                single<ClientRepository> { ClientRepository() }
                single<TrashRepository> { TrashRepository() }
                single<SubmissionRepository> { SubmissionRepository() }
                single<SubmissionHistoryRepository> { SubmissionHistoryRepository() }
                single<ImageRepository> { ImageRepository() }
                single<MetadataRepository> { MetadataRepository() }
                single<PointRepository> { PointRepository() }

                // Storage
                single<MinIOStorageService>(createdAtStart = true) {
                    MinIOStorageService(
                        endpoint = getRequiredEnv(Env.MINIO_ENDPOINT),
                        accessKey = getRequiredEnv(Env.MINIO_ACCESS_KEY),
                        secretKey = getRequiredEnv(Env.MINIO_SECRET_KEY),
                        bucketName = getRequiredEnv(Env.MINIO_BUCKET)
                    )
                }

                // RabbitMQ
                single<RabbitMQClient>(createdAtStart = true) { RabbitMQClient() }

                // Services
                single<AuthService> {
                    AuthService(
                        adminRepository = get(),
                        clientRepository = get(),
                        agentRepository = get(),
                        refreshTokenRepository = get()
                    )
                }
                single<ImageService> {
                    ImageService(
                        imageRepository = get(),
                        storageService = get()
                    )
                }
                single<ClientService> {
                    ClientService(
                        clientRepository = get(),
                        pointRepository = get()
                    )
                }
                single<AgentService> {
                    AgentService(
                        agentRepository = get()
                    )
                }
                single<TrashService> {
                    TrashService(
                        trashRepository = get()
                    )
                }
                single<SubmissionService> {
                    SubmissionService(
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
                single<MachineLearningService> {
                    MachineLearningService(
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
