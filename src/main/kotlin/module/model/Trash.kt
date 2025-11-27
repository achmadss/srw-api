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
    companion object: EntityClass<String, Trash>(_root_ide_package_.module.model.TrashTable)
    val name: String get() = id.value
    var pointsPerUnit by _root_ide_package_.module.model.TrashTable.pointsPerUnit
    var createdAt by _root_ide_package_.module.model.TrashTable.createdAt
    var updatedAt by _root_ide_package_.module.model.TrashTable.updatedAt
    val metadata by _root_ide_package_.module.model.Metadata referrersOn _root_ide_package_.module.model.MetadataTable.trash
}

@OptIn(ExperimentalTime::class)
fun module.model.Trash.toResponse(): module.service.TrashResponse {
    return _root_ide_package_.module.service.TrashResponse(
        name = this.name,
        pointsPerUnit = this.pointsPerUnit,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}