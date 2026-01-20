package config

import com.srw.util.inject
import io.ktor.server.application.*
import model.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.AdminRepository
import repository.TrashRepository
import java.sql.Statement

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
        runMigrations()
        inject<AdminRepository>().seedDefaultAdmin()
        inject<TrashRepository>().seedTrashTypesFromConfig()
    }
}

private fun runMigrations() {
    // Migration: Drop deprecated url column from images table
    // This column was used to store full presigned URLs, but now we generate URLs at runtime
    try {
        transaction {
            // Access low-level JDBC connection to execute raw SQL
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            statement.execute("ALTER TABLE images DROP COLUMN IF EXISTS url")
            statement.close()
        }
        println("Migration: Dropped deprecated 'url' column from 'images' table (if existed)")
    } catch (e: Exception) {
        // Table or column might not exist yet, ignore
        println("Migration: No migration needed or table doesn't exist yet")
    }
}