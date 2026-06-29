// Environment Variable Keys
object Env {
    // Database
    const val DB_URL = "DB_URL"
    const val DB_USER = "DB_USER"
    const val DB_PASSWORD = "DB_PASSWORD"

    // JWT Configuration
    const val JWT_ISSUER = "JWT_ISSUER"
    const val ADMIN_JWT_AUDIENCE = "ADMIN_JWT_AUDIENCE"
    const val CLIENT_JWT_AUDIENCE = "CLIENT_JWT_AUDIENCE"
    const val AGENT_JWT_AUDIENCE = "AGENT_JWT_AUDIENCE"

    // JWT Secrets
    const val ADMIN_JWT_SECRET = "ADMIN_JWT_SECRET"
    const val CLIENT_JWT_SECRET = "CLIENT_JWT_SECRET"
    const val AGENT_JWT_SECRET = "AGENT_JWT_SECRET"

    // Optional - Default Admin
    const val DEFAULT_ADMIN_USERNAME = "DEFAULT_ADMIN_USERNAME"
    const val DEFAULT_ADMIN_PASSWORD = "DEFAULT_ADMIN_PASSWORD"

    // MinIO Storage
    const val MINIO_HOSTNAME = "MINIO_HOSTNAME"
    const val MINIO_PUBLIC_HOSTNAME = "MINIO_PUBLIC_HOSTNAME"
    const val MINIO_ACCESS_KEY = "MINIO_ACCESS_KEY"
    const val MINIO_SECRET_KEY = "MINIO_SECRET_KEY"
    const val MINIO_BUCKET = "MINIO_BUCKET"

    // RabbitMQ
    const val RABBITMQ_URL = "RABBITMQ_URL"

    // Trash Types Configuration
    const val TRASH_TYPES_CONFIG_PATH = "TRASH_TYPES_CONFIG_PATH"
    const val DEFAULT_TRASH_POINTS_PER_UNIT = "DEFAULT_TRASH_POINTS_PER_UNIT"

    // AES Encryption
    const val FIELD_ENCRYPTION_KEY = "FIELD_ENCRYPTION_KEY"

    // API Logging Configuration
    const val API_LOG_LEVEL = "API_LOG_LEVEL"
    const val API_LOG_REQUEST_BODY = "API_LOG_REQUEST_BODY"
    const val API_LOG_REQUEST_HEADERS = "API_LOG_REQUEST_HEADERS"
    const val API_LOG_RESPONSE_BODY = "API_LOG_RESPONSE_BODY"
    const val API_LOG_RESPONSE_HEADERS = "API_LOG_RESPONSE_HEADERS"
}

object JwtAuth {
    const val ADMIN = "jwt-auth-admin"
    const val CLIENT = "jwt-auth-client"
    const val AGENT = "jwt-auth-agent"
}

enum class UserType(val value: String) {
    ADMIN("admin"),
    CLIENT("client"),
    AGENT("agent");

    companion object {
        operator fun invoke(value: String?): UserType? {
            return when(value) {
                "admin" -> ADMIN
                "client" -> CLIENT
                "agent" -> AGENT
                else -> null
            }
        }
    }
}

/**
 * Validates that all required environment variables are set.
 * Throws IllegalStateException if any required variable is missing or empty.
 */
fun validateEnvironmentVariables() {
    val required = listOf(
        Env.DB_URL,
        Env.DB_USER,
        Env.DB_PASSWORD,
        Env.JWT_ISSUER,
        Env.ADMIN_JWT_AUDIENCE,
        Env.CLIENT_JWT_AUDIENCE,
        Env.AGENT_JWT_AUDIENCE,
        Env.ADMIN_JWT_SECRET,
        Env.CLIENT_JWT_SECRET,
        Env.AGENT_JWT_SECRET,
        Env.MINIO_HOSTNAME,
        Env.MINIO_PUBLIC_HOSTNAME,
        Env.MINIO_ACCESS_KEY,
        Env.MINIO_SECRET_KEY,
        Env.MINIO_BUCKET,
        Env.RABBITMQ_URL,
        Env.FIELD_ENCRYPTION_KEY
    )

    val missing = mutableListOf<String>()

    for (key in required) {
        val value = System.getenv(key)
        if (value.isNullOrBlank()) {
            missing.add(key)
        }
    }

    if (missing.isNotEmpty()) {
        throw IllegalStateException(
            "Missing or empty required environment variables:\n${missing.joinToString("\n") { "  - $it" }}"
        )
    }
}

/**
 * Gets an environment variable value, throwing an exception if it's missing.
 */
fun getRequiredEnv(key: String): String {
    return System.getenv(key) ?: throw IllegalStateException("Required environment variable '$key' is not set")
}

/**
 * Gets an optional environment variable value with a default.
 */
fun getOptionalEnv(key: String, default: String): String {
    return System.getenv(key)?.takeIf { it.isNotBlank() } ?: default
}
