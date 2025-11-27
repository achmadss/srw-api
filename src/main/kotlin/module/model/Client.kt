package module.model

import resource.client.ClientResponse
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
    companion object: IntEntityClass<Client>(_root_ide_package_.module.model.ClientTable)
    var name by _root_ide_package_.module.model.ClientTable.name
    var nfc by _root_ide_package_.module.model.ClientTable.nfc
    var address by _root_ide_package_.module.model.ClientTable.address
    var createdAt by _root_ide_package_.module.model.ClientTable.createdAt
    var updatedAt by _root_ide_package_.module.model.ClientTable.updatedAt

    val submissions by _root_ide_package_.module.model.Submission.Companion referrersOn _root_ide_package_.module.model.SubmissionTable.client
    val points by _root_ide_package_.module.model.Point.Companion referrersOn _root_ide_package_.module.model.PointTable.client
}

/**
 * Extension function to convert Client entity to ClientResponse within a transaction
 * Note: totalPoints must be calculated separately and passed as parameter
 */
@OptIn(ExperimentalTime::class)
fun module.model.Client.toResponse(totalPoints: Int): ClientResponse {
    return transaction {
        ClientResponse(
            nfc = this@toResponse.nfc,
            name = this@toResponse.name,
            address = this@toResponse.address,
            totalPoints = totalPoints
        )
    }
}