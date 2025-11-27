package module.v1.model

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
    val submission = reference("submission_id", SubmissionTable, onDelete = ReferenceOption.CASCADE)
    val mlStatus = varchar("ml_status", 50).default(MLStatus.PENDING.name)
    val mlError = varchar("ml_error", 500).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Image(id: EntityID<String>): Entity<String>(id) {
    companion object: EntityClass<String, Image>(ImageTable)
    var url by ImageTable.url
    var submission by Submission.Companion referencedOn ImageTable.submission
    var mlStatus by ImageTable.mlStatus
    var mlError by ImageTable.mlError
    var createdAt by ImageTable.createdAt
    var updatedAt by ImageTable.updatedAt
    val metadata by Metadata.Companion referrersOn MetadataTable.image

    fun getMLStatus(): MLStatus = MLStatus.valueOf(mlStatus)
}