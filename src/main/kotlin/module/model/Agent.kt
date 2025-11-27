package module.model

import module.service.AgentResponse
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object AgentTable : IntIdTable("agents") {
    val name = text("name")
    val username = text("username").uniqueIndex()
    val password = text("password")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

@OptIn(ExperimentalTime::class)
class Agent(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<Agent>(_root_ide_package_.module.model.AgentTable)
    var name by _root_ide_package_.module.model.AgentTable.name
    var username by _root_ide_package_.module.model.AgentTable.username
    var password by _root_ide_package_.module.model.AgentTable.password
    var createdAt by _root_ide_package_.module.model.AgentTable.createdAt
    var updatedAt by _root_ide_package_.module.model.AgentTable.updatedAt
    val submissions by _root_ide_package_.module.model.Submission.Companion optionalReferrersOn _root_ide_package_.module.model.SubmissionTable.agent
}

/**
 * Extension function to convert Agent entity to AgentResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun module.model.Agent.toResponse(): module.service.AgentResponse {
    return transaction {
        _root_ide_package_.module.service.AgentResponse(
            id = this@toResponse.id.value,
            name = this@toResponse.name,
            username = this@toResponse.username,
            createdAt = this@toResponse.createdAt,
            updatedAt = this@toResponse.updatedAt
        )
    }
}

/**
 * Extension function to convert List of Agent entities to List of AgentResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun List<module.model.Agent>.toResponses(): List<module.service.AgentResponse> {
    return transaction {
        this@toResponses.map { agent ->
            _root_ide_package_.module.service.AgentResponse(
                id = agent.id.value,
                name = agent.name,
                username = agent.username,
                createdAt = agent.createdAt,
                updatedAt = agent.updatedAt
            )
        }
    }
}