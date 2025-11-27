package module.repository

import module.v1.model.Agent
import module.v1.model.AgentTable
import org.jetbrains.exposed.v1.core.eq
import util.PasswordUtil
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AgentRepository {
    fun create(name: String, username: String, password: String): Agent {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }

        val existingAgent = Agent.find { AgentTable.username eq username }.firstOrNull()
        require(existingAgent == null) { "Agent with username '$username' already exists" }

        val now = Clock.System.now()
        return Agent.new {
            this.name = name
            this.username = username
            this.password = PasswordUtil.hashPassword(password)
            this.createdAt = now
            this.updatedAt = now
        }
    }

    fun findById(id: Int): Agent? {
        return Agent.findById(id)
    }

    fun findByUsername(username: String): Agent? {
        return Agent.find { AgentTable.username eq username }.firstOrNull()
    }

    fun findAll(): List<Agent> {
        return Agent.all().toList()
    }

    fun update(id: Int, name: String?, username: String?, password: String?): Agent {
        val agent = Agent.findById(id) ?: throw IllegalArgumentException("Agent with id $id not found")

        name?.let {
            require(it.isNotBlank()) { "Name cannot be blank" }
            agent.name = it
        }

        username?.let {
            require(it.isNotBlank()) { "Username cannot be blank" }
            val existingAgent = Agent.find { AgentTable.username eq it }.firstOrNull()
            if (existingAgent != null && existingAgent.id.value != id) {
                throw IllegalArgumentException("Agent with username '$it' already exists")
            }
            agent.username = it
        }

        password?.let {
            require(it.isNotBlank()) { "Password cannot be blank" }
            agent.password = PasswordUtil.hashPassword(it)
        }

        agent.updatedAt = Clock.System.now()
        return agent
    }

    fun delete(id: Int) {
        val agent = Agent.findById(id) ?: throw IllegalArgumentException("Agent with id $id not found")
        agent.delete()
    }

    fun verifyPassword(agent: Agent, password: String): Boolean {
        return PasswordUtil.verifyPassword(password, agent.password)
    }
}