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
    companion object : IntEntityClass<Admin>(AdminTable)

    var username by AdminTable.username
    var password by AdminTable.password
    var createdAt by AdminTable.createdAt
    var updatedAt by AdminTable.updatedAt
}
