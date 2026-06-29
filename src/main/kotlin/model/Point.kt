package model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object PointTable : IntIdTable("points") {
    val client = reference("client_id", ClientTable)
    val submission = reference("submission_id", SubmissionTable, onDelete = ReferenceOption.SET_NULL).nullable() // null for manual adjustments
    val amount = text("amount") // encrypted ciphertext for add (+), deduction (-)
    val createdAt = timestamp("created_at")
}

@OptIn(ExperimentalTime::class)
class Point(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Point>(PointTable)
    var client by Client referencedOn PointTable.client
    var submission by Submission.Companion optionalReferencedOn PointTable.submission
    var amount by PointTable.amount
    var createdAt by PointTable.createdAt
}
