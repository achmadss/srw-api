package module.model

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime
import module.service.TrashResponse

@OptIn(ExperimentalTime::class)
object TrashTable: IdTable<String>("trashes") {
    override val id = varchar("name", 255).entityId()
    override val primaryKey = PrimaryKey(id)
    val pointsPerUnit = integer("points_per_unit")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Trash(id: EntityID<String>): Entity<String>(id) {
    companion object: EntityClass<String, Trash>(TrashTable)
    val name: String get() = id.value
    var pointsPerUnit by TrashTable.pointsPerUnit
    var createdAt by TrashTable.createdAt
    var updatedAt by TrashTable.updatedAt
    val metadata by Metadata referrersOn MetadataTable.trash
}

@OptIn(ExperimentalTime::class)
fun Trash.toResponse(): TrashResponse {
    return TrashResponse(
        name = this.name,
        pointsPerUnit = this.pointsPerUnit,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}