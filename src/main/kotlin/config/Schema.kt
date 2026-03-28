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

    // Migration: Add latitude and longitude columns to clients table
    try {
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            statement.execute("ALTER TABLE clients ADD COLUMN IF NOT EXISTS latitude FLOAT")
            statement.execute("ALTER TABLE clients ADD COLUMN IF NOT EXISTS longitude FLOAT")
            statement.close()
        }
        println("Migration: Added latitude and longitude columns to 'clients' table")
    } catch (e: Exception) {
        println("Migration: Could not add latitude/longitude columns - may already exist")
    }

    // Migration: Make address column nullable in clients table
    try {
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            statement.execute("ALTER TABLE clients ALTER COLUMN address DROP NOT NULL")
            statement.close()
        }
        println("Migration: Made 'address' column nullable in 'clients' table")
    } catch (e: Exception) {
        println("Migration: Could not make address nullable - may already be nullable or column doesn't exist")
    }
}