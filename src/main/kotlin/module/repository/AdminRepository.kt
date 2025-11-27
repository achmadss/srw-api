package module.repository

import Env
import getOptionalEnv
import module.model.Admin
import module.model.AdminTable
import org.jetbrains.exposed.v1.core.eq
import util.PasswordUtil
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AdminRepository {
    fun create(username: String, password: String): module.model.Admin {
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }

        val existingAdmin = _root_ide_package_.module.model.Admin.find { _root_ide_package_.module.model.AdminTable.username eq username }.firstOrNull()
        require(existingAdmin == null) { "Admin with username '$username' already exists" }

        val now = Clock.System.now()
        return _root_ide_package_.module.model.Admin.new {
            this.username = username
            this.password = PasswordUtil.hashPassword(password)
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findById(id: Int): module.model.Admin? {
        return _root_ide_package_.module.model.Admin.findById(id)
    }

    fun findByUsername(username: String): module.model.Admin? {
        return _root_ide_package_.module.model.Admin.find { _root_ide_package_.module.model.AdminTable.username eq username }.firstOrNull()
    }

    fun findAll(): List<module.model.Admin> {
        return Admin.all().toList()
    }

    fun update(id: Int, username: String?, password: String?): Admin {
        val admin = Admin.findById(id) ?: throw IllegalArgumentException("Admin with id $id not found")

        username?.let {
            require(it.isNotBlank()) { "Username cannot be blank" }
            val existingAdmin = Admin.find { AdminTable.username eq it }.firstOrNull()
            if (existingAdmin != null && existingAdmin.id.value != id) {
                throw IllegalArgumentException("Admin with username '$it' already exists")
            }
            admin.username = it
        }

        password?.let {
            require(it.isNotBlank()) { "Password cannot be blank" }
            admin.password = PasswordUtil.hashPassword(it)
        }

        admin.updatedAt = Clock.System.now()
        return admin
    }

    fun delete(id: Int) {
        val admin = Admin.findById(id) ?: throw IllegalArgumentException("Admin with id $id not found")
        admin.delete()
    }

    fun seedDefaultAdmin(): Admin? {
        val existingAdmins = Admin.all().count()
        if (existingAdmins == 0L) {
            val defaultUsername = getOptionalEnv(Env.DEFAULT_ADMIN_USERNAME, "admin")

            // Generate random password if not provided in environment
            val envPassword = System.getenv(Env.DEFAULT_ADMIN_PASSWORD)?.takeIf { it.isNotBlank() }
            val defaultPassword = envPassword ?: generateRandomPassword()
            val isPasswordGenerated = envPassword == null

            val now = Clock.System.now()
            val newAdmin = Admin.new {
                this.username = defaultUsername
                this.password = PasswordUtil.hashPassword(defaultPassword)
                this.createdAt = now
                this.updatedAt = now
            }

            // Log default admin credentials for first-time setup
            println("=" .repeat(80))
            println("DEFAULT ADMIN ACCOUNT CREATED")
            println("=" .repeat(80))
            println("Username: $defaultUsername")
            println("Password: $defaultPassword")
            if (isPasswordGenerated) {
                println("(Password was randomly generated)")
            }
            println("=" .repeat(80))
            println("IMPORTANT: Change this password immediately after first login!")
            println("=" .repeat(80))

            return newAdmin
        } else {
            return null
        }
    }

    /**
     * Generate a secure random password
     */
    private fun generateRandomPassword(length: Int = 16): String {
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('!', '@', '#', '$', '%', '^', '&', '*')
        return (1..length)
            .map { chars[Random.nextInt(chars.size)] }
            .joinToString("")
    }
}