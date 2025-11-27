package module.model

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object RefreshTokenTable : IntIdTable("refresh_tokens") {
    val token = text("token").uniqueIndex()
    val userId = integer("user_id")
    val userType = text("user_type") // "admin", "agent", or "client"
    val expiresAt = timestamp("expires_at")
    val isRevoked = bool("is_revoked").default(false)
    val createdAt = timestamp("created_at")
}

@OptIn(ExperimentalTime::class)
class RefreshToken(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RefreshToken>(_root_ide_package_.module.model.RefreshTokenTable)

    var token by _root_ide_package_.module.model.RefreshTokenTable.token
    var userId by _root_ide_package_.module.model.RefreshTokenTable.userId
    var userType by _root_ide_package_.module.model.RefreshTokenTable.userType
    var expiresAt by _root_ide_package_.module.model.RefreshTokenTable.expiresAt
    var isRevoked by _root_ide_package_.module.model.RefreshTokenTable.isRevoked
    var createdAt by _root_ide_package_.module.model.RefreshTokenTable.createdAt
}
