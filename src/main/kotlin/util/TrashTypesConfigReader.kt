package util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Data class representing the trash types configuration file structure
 */
@Serializable
data class TrashTypesConfig(
    val version: String,
    val lastUpdated: String,
    val trashTypes: List<TrashTypeItem>,
    val mlMappings: Map<String, String>
)

/**
 * Single trash type item in the configuration
 */
@Serializable
data class TrashTypeItem(
    val name: String
)

/**
 * Utility object for reading and parsing the trash types configuration file
 */
object TrashTypesConfigReader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * Reads and parses the trash types configuration file
     *
     * @param configPath Absolute or relative path to the trash-types.json file
     * @return List of trash type names
     * @throws IllegalStateException if the config file is missing, invalid, or empty
     */
    fun readTrashTypes(configPath: String): List<String> {
        val configFile = File(configPath)

        // Validate file exists
        if (!configFile.exists()) {
            throw IllegalStateException(
                "Trash types configuration file not found at: $configPath\n" +
                "Please create this file or set TRASH_TYPES_CONFIG_PATH environment variable."
            )
        }

        // Validate file is readable
        if (!configFile.canRead()) {
            throw IllegalStateException(
                "Cannot read trash types configuration file at: $configPath\n" +
                "Please check file permissions."
            )
        }

        // Read and parse JSON
        val config = try {
            val jsonContent = configFile.readText()
            json.decodeFromString<TrashTypesConfig>(jsonContent)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse trash types configuration file at: $configPath\n" +
                "Error: ${e.message}\n" +
                "Please ensure the file contains valid JSON.",
                e
            )
        }

        // Validate trash types array is not empty
        if (config.trashTypes.isEmpty()) {
            throw IllegalStateException(
                "Trash types configuration is empty at: $configPath\n" +
                "The 'trashTypes' array must contain at least one trash type."
            )
        }

        // Validate all trash type names are not blank
        val blankNames = config.trashTypes.filter { it.name.isBlank() }
        if (blankNames.isNotEmpty()) {
            throw IllegalStateException(
                "Trash types configuration contains blank names at: $configPath\n" +
                "All trash type names must be non-blank strings."
            )
        }

        // Validate ML mappings point to valid trash types
        val trashTypeNames = config.trashTypes.map { it.name }.toSet()
        val invalidMappings = config.mlMappings.filter { (_, target) ->
            target !in trashTypeNames
        }
        if (invalidMappings.isNotEmpty()) {
            val invalidList = invalidMappings.entries.joinToString(", ") { "\"${it.key}\" -> \"${it.value}\"" }
            throw IllegalStateException(
                "Trash types configuration contains invalid ML mappings at: $configPath\n" +
                "The following mappings point to non-existent trash types: $invalidList\n" +
                "All ML mappings must point to valid trash types."
            )
        }

        return config.trashTypes.map { it.name }
    }

    /**
     * Reads the full configuration including ML mappings
     *
     * @param configPath Absolute or relative path to the trash-types.json file
     * @return Full TrashTypesConfig object
     * @throws IllegalStateException if the config file is missing, invalid, or empty
     */
    fun readConfig(configPath: String): TrashTypesConfig {
        readTrashTypes(configPath) // Validates the config

        val configFile = File(configPath)
        val jsonContent = configFile.readText()
        return json.decodeFromString<TrashTypesConfig>(jsonContent)
    }
}
