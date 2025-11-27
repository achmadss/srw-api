package module.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object MetadataTable : IntIdTable("metadatas") {
    val amount = integer("amount")
    val image = reference("image_id", _root_ide_package_.module.model.ImageTable, onDelete = ReferenceOption.CASCADE)
    val trash = reference("trash_id", _root_ide_package_.module.model.TrashTable)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Metadata(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Metadata>(_root_ide_package_.module.model.MetadataTable)
    var image by _root_ide_package_.module.model.Image referencedOn _root_ide_package_.module.model.MetadataTable.image
    var amount by _root_ide_package_.module.model.MetadataTable.amount
    var trash by _root_ide_package_.module.model.Trash.Companion referencedOn _root_ide_package_.module.model.MetadataTable.trash
    var createdAt by _root_ide_package_.module.model.MetadataTable.createdAt
    var updatedAt by _root_ide_package_.module.model.MetadataTable.updatedAt
}
