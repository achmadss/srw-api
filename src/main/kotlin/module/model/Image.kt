package module.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

enum class MLStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

@OptIn(ExperimentalTime::class)
object ImageTable : IdTable<String>("images") {
    override val id = varchar("id", 255).entityId()
    override val primaryKey = PrimaryKey(id)
    val url = text("url")
    val submission = reference("submission_id", _root_ide_package_.module.model.SubmissionTable, onDelete = ReferenceOption.CASCADE)
    val mlStatus = varchar("ml_status", 50).default(_root_ide_package_.module.model.MLStatus.PENDING.name)
    val mlError = varchar("ml_error", 500).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Image(id: EntityID<String>): Entity<String>(id) {
    companion object: EntityClass<String, Image>(_root_ide_package_.module.model.ImageTable)
    var url by _root_ide_package_.module.model.ImageTable.url
    var submission by _root_ide_package_.module.model.Submission.Companion referencedOn _root_ide_package_.module.model.ImageTable.submission
    var mlStatus by _root_ide_package_.module.model.ImageTable.mlStatus
    var mlError by _root_ide_package_.module.model.ImageTable.mlError
    var createdAt by _root_ide_package_.module.model.ImageTable.createdAt
    var updatedAt by _root_ide_package_.module.model.ImageTable.updatedAt
    val metadata by _root_ide_package_.module.model.Metadata.Companion referrersOn _root_ide_package_.module.model.MetadataTable.image

    fun getMLStatus(): module.model.MLStatus = _root_ide_package_.module.model.MLStatus.valueOf(mlStatus)
}