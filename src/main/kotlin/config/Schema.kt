package config

import com.srw.util.inject
import io.ktor.server.application.*
import model.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.AdminRepository
import repository.TrashRepository
import util.AesUtil
import javax.crypto.SecretKey
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

    // Migration: Add submission location columns to submissions table
    try {
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            
            // Check if column exists first
            val rs = statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'submissions' AND column_name = 'submission_address'")
            if (!rs.next()) {
                statement.execute("ALTER TABLE submissions ADD COLUMN submission_address TEXT")
                statement.execute("ALTER TABLE submissions ADD COLUMN submission_latitude FLOAT")
                statement.execute("ALTER TABLE submissions ADD COLUMN submission_longitude FLOAT")
                println("Migration: Added submission location columns to 'submissions' table")
            } else {
                println("Migration: submission_address column already exists")
            }
            rs.close()
            statement.close()
        }
    } catch (e: Exception) {
        println("Migration ERROR: Could not add submission location columns - ${e.message}")
    }

    // Migration: Migrate pickup_location data to submission_address
    try {
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            statement.execute("UPDATE submissions SET submission_address = pickup_location WHERE submission_address IS NULL AND pickup_location IS NOT NULL")
            val updatedRows = statement.updateCount
            statement.close()
            println("Migration: Migrated $updatedRows rows from pickup_location to submission_address")
        }
    } catch (e: Exception) {
        println("Migration ERROR: Could not migrate pickup_location data - ${e.message}")
    }

    // Migration: Drop old pickup_location column
    try {
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            statement.execute("ALTER TABLE submissions DROP COLUMN IF EXISTS pickup_location")
            statement.close()
        }
        println("Migration: Dropped 'pickup_location' column from 'submissions' table")
    } catch (e: Exception) {
        println("Migration ERROR: Could not drop pickup_location column - ${e.message}")
    }

    // Migration: Convert points.amount from INTEGER to TEXT for AES encryption
    try {
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val statement = connection.createStatement()
            statement.execute("ALTER TABLE points ALTER COLUMN amount TYPE TEXT USING amount::TEXT")
            statement.close()
        }
        println("Migration: Converted points.amount from INTEGER to TEXT")
    } catch (e: Exception) {
        println("Migration: points.amount already TEXT or table doesn't exist — ${e.message}")
    }

    // Migration: Encrypt existing plaintext points.amount values
    try {
        val aesKey = inject<SecretKey>()
        transaction {
            val connection = this.connection.connection as java.sql.Connection
            val stmt = connection.prepareStatement("SELECT id, amount FROM points")
            val rs = stmt.executeQuery()

            val updates = mutableListOf<Pair<Int, String>>()
            while (rs.next()) {
                val id = rs.getInt("id")
                val rawAmount = rs.getString("amount")
                if (rawAmount.toIntOrNull() != null) {
                    val encrypted = AesUtil.encryptInt(rawAmount.toInt(), aesKey)
                    updates.add(id to encrypted)
                }
            }
            rs.close()
            stmt.close()

            if (updates.isNotEmpty()) {
                val updateStmt = connection.prepareStatement("UPDATE points SET amount = ? WHERE id = ?")
                for ((id, encrypted) in updates) {
                    updateStmt.setString(1, encrypted)
                    updateStmt.setInt(2, id)
                    updateStmt.executeUpdate()
                }
                updateStmt.close()
                println("Migration: Encrypted ${updates.size} legacy point rows")
            } else {
                println("Migration: No legacy point rows to encrypt")
            }
        }
    } catch (e: Exception) {
        println("Migration ERROR: Could not encrypt legacy points — ${e.message}")
    }
}