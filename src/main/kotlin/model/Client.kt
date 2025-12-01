package model

import model.response.ClientResponse
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object ClientTable : IntIdTable("clients") {
    val name = text("name")
    val nfc = text("nfc")
    val address = text("address")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Client(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<Client>(ClientTable)
    var name by ClientTable.name
    var nfc by ClientTable.nfc
    var address by ClientTable.address
    var createdAt by ClientTable.createdAt
    var updatedAt by ClientTable.updatedAt

    val submissions by Submission.Companion referrersOn SubmissionTable.client
    val points by Point.Companion referrersOn PointTable.client
}

/**
 * Extension function to convert Client entity to ClientResponse within a transaction
 * Note: totalPoints must be calculated separately and passed as parameter
 */
@OptIn(ExperimentalTime::class)
fun Client.toClientResponse(totalPoints: Int): ClientResponse {
    return transaction {
        ClientResponse(
            id = this@toClientResponse.id.value,
            nfc = this@toClientResponse.nfc,
            name = this@toClientResponse.name,
            address = this@toClientResponse.address,
            totalPoints = totalPoints
        )
    }
}