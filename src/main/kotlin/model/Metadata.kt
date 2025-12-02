package model

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
    val image = reference("image_id", ImageTable, onDelete = ReferenceOption.CASCADE)
    val trash = reference("trash_id", TrashTable)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Metadata(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Metadata>(MetadataTable)
    var image by Image referencedOn MetadataTable.image
    var amount by MetadataTable.amount
    var trash by Trash.Companion referencedOn MetadataTable.trash
    var createdAt by MetadataTable.createdAt
    var updatedAt by MetadataTable.updatedAt
}
