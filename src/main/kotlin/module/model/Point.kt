package module.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object PointTable : IntIdTable("points") {
    val client = reference("client_id", _root_ide_package_.module.model.ClientTable)
    val submission = reference("submission_id", _root_ide_package_.module.model.SubmissionTable, onDelete = ReferenceOption.SET_NULL).nullable() // null for manual adjustments
    val amount = integer("amount") // + for add, - for deduction
    val createdAt = timestamp("created_at")
}

@OptIn(ExperimentalTime::class)
class Point(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Point>(_root_ide_package_.module.model.PointTable)
    var client by _root_ide_package_.module.model.Client referencedOn _root_ide_package_.module.model.PointTable.client
    var submission by _root_ide_package_.module.model.Submission.Companion optionalReferencedOn _root_ide_package_.module.model.PointTable.submission
    var amount by _root_ide_package_.module.model.PointTable.amount
    var createdAt by _root_ide_package_.module.model.PointTable.createdAt
}
