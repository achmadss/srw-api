package module.model

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object AdminTable : IntIdTable("admins") {
    val username = text("username").uniqueIndex()
    val password = text("password")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Admin(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Admin>(_root_ide_package_.module.model.AdminTable)

    var username by _root_ide_package_.module.model.AdminTable.username
    var password by _root_ide_package_.module.model.AdminTable.password
    var createdAt by _root_ide_package_.module.model.AdminTable.createdAt
    var updatedAt by _root_ide_package_.module.model.AdminTable.updatedAt
}
