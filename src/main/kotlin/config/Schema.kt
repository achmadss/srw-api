package config

import com.srw.util.inject
import io.ktor.server.application.*
import model.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.AdminRepository
import repository.TrashRepository

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