package config

import Env
import getRequiredEnv
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import repository.*
import service.*
import util.RabbitMQClient

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
                single<PointService> {
                    PointService(
                        pointRepository = get(),
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
