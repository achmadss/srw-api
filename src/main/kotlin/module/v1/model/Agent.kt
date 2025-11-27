package module.v1.model

import module.v1.service.AgentResponse
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
    companion object: IntEntityClass<Agent>(AgentTable)
    var name by AgentTable.name
    var username by AgentTable.username
    var password by AgentTable.password
    var createdAt by AgentTable.createdAt
    var updatedAt by AgentTable.updatedAt
    val submissions by Submission.Companion optionalReferrersOn SubmissionTable.agent
}

/**
 * Extension function to convert Agent entity to AgentResponse within a transaction
 */
@OptIn(ExperimentalTime::class)
fun Agent.toResponse(): AgentResponse {
    return transaction {
        AgentResponse(
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
fun List<Agent>.toResponses(): List<AgentResponse> {
    return transaction {
        this@toResponses.map { agent ->
            AgentResponse(
                id = agent.id.value,
                name = agent.name,
                username = agent.username,
                createdAt = agent.createdAt,
                updatedAt = agent.updatedAt
            )
        }
    }
}