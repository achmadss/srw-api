package repository

import Env
import getOptionalEnv
import model.Trash
import util.TrashTypesConfigReader
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TrashRepository {
    fun create(name: String, pointsPerUnit: Int): Trash {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(pointsPerUnit >= 0) { "Points per unit must be non-negative" }

        // Check if trash with this name already exists
        val existing = Trash.findById(name)
        require(existing == null) { "Trash with name '$name' already exists" }

        val now = Clock.System.now()
        return Trash.new(name) {
            this.pointsPerUnit = pointsPerUnit
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findById(name: String): Trash? {
        return Trash.findById(name)
    }

    fun findByName(name: String): Trash? {
        return findById(name)
    }

    fun findAll(): List<Trash> {
        return Trash.all().toList()
    }

    fun update(name: String, pointsPerUnit: Int?): Trash {
        val trash = Trash.findById(name) ?: throw IllegalArgumentException("Trash with name '$name' not found")

        pointsPerUnit?.let {
            require(it >= 0) { "Points per unit must be non-negative" }
            trash.pointsPerUnit = it
        }

        trash.updatedAt = Clock.System.now()
        return trash
    }

    fun delete(name: String) {
        val trash = Trash.findById(name) ?: throw IllegalArgumentException("Trash with name '$name' not found")
        trash.delete()
    }

    /**
     * Seeds trash types from the configuration file.
     * This method is idempotent - it only creates trash types that don't exist yet.
     * Existing trash types are preserved with their custom pointsPerUnit values.
     */
    fun seedTrashTypesFromConfig() {
        // Get config path from environment or use default
        val configPath = getOptionalEnv(Env.TRASH_TYPES_CONFIG_PATH, "trash-types.json")

        // Get default points per unit from environment or use default
        val defaultPointsStr = getOptionalEnv(Env.DEFAULT_TRASH_POINTS_PER_UNIT, "10")
        val defaultPoints = defaultPointsStr.toIntOrNull() ?: 10

        // Load trash types from config (will throw if config is invalid)
        val trashTypes = try {
            TrashTypesConfigReader.readTrashTypes(configPath)
        } catch (e: Exception) {
            println("=" .repeat(80))
            println("ERROR: Failed to load trash types configuration")
            println("=" .repeat(80))
            println("Error: ${e.message}")
            println("=" .repeat(80))
            throw e
        }

        // Track seeding results
        val created = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        // Create trash types that don't exist
        trashTypes.forEach { typeName ->
            val existing = Trash.findById(typeName)
            if (existing == null) {
                val now = Clock.System.now()
                Trash.new(typeName) {
                    this.pointsPerUnit = defaultPoints
                    this.createdAt = now
                    this.updatedAt = now
                }
                created.add(typeName)
            } else {
                skipped.add(typeName)
            }
        }

        // Log seeding results
        println("=" .repeat(80))
        println("TRASH TYPES SEEDING COMPLETED")
        println("=" .repeat(80))
        println("Config file: $configPath")
        println("Total types in config: ${trashTypes.size}")
        println("Created: ${created.size}")
        if (created.isNotEmpty()) {
            println("  - ${created.joinToString(", ")}")
            println("  (Default points per unit: $defaultPoints)")
        }
        println("Skipped (already exist): ${skipped.size}")
        if (skipped.isNotEmpty()) {
            println("  - ${skipped.joinToString(", ")}")
        }
        println("=" .repeat(80))
    }
}