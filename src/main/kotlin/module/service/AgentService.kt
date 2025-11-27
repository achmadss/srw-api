package module.service

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import module.model.toResponse
import module.model.toResponses
import module.model.response.BaseResponse
import module.model.response.PaginatedResponse
import module.repository.AgentRepository
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Service for agent management operations
 */
class AgentService(
    private val agentRepository: AgentRepository
) {

    /**
     * Create a new agent
     */
    fun create(
        name: String,
        username: String,
        password: String
    ): Pair<HttpStatusCode, BaseResponse<AgentResponse>> {
        return transaction {
            try {
                val agent = agentRepository.create(
                    name = name,
                    username = username,
                    password = password
                )

                HttpStatusCode.Created to BaseResponse(
                    success = true,
                    code = HttpStatusCode.Created.value,
                    message = "Agent created successfully",
                    data = agent.toResponse()
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to create agent",
                    data = null
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = "Internal server error",
                    data = null
                )
            }
        }
    }

    /**
     * Get agent by ID
     */
    fun getById(id: Int): Pair<HttpStatusCode, BaseResponse<AgentResponse?>> {
        return transaction {
            val agent = agentRepository.findById(id)
            if (agent != null) {
                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = agent.toResponse()
                )
            } else {
                HttpStatusCode.NotFound to BaseResponse(
                    success = false,
                    code = HttpStatusCode.NotFound.value,
                    message = "Agent with id $id not found",
                    data = null
                )
            }
        }
    }

    /**
     * Get all agents (paginated)
     */
    fun getPaginated(
        page: Int,
        pageSize: Int
    ): Pair<HttpStatusCode, BaseResponse<PaginatedResponse<AgentResponse>>> {
        return transaction {
            val validPage = if (page >= 1) page else 1
            val validPageSize = if (pageSize >= 1) pageSize else 20

            val allAgents = agentRepository.findAll()
            val total = allAgents.size
            val totalPages = if (total == 0) 1 else ((total + validPageSize - 1) / validPageSize)

            val paginatedAgents = allAgents
                .drop((validPage - 1) * validPageSize)
                .take(validPageSize)

            HttpStatusCode.OK to BaseResponse(
                success = true,
                code = HttpStatusCode.OK.value,
                data = PaginatedResponse(
                    data = paginatedAgents.toResponses(),
                    page = validPage,
                    pageSize = validPageSize,
                    total = total,
                    totalPages = totalPages
                )
            )
        }
    }

    /**
     * Get all agents (non-paginated)
     */
    fun getAll(): Pair<HttpStatusCode, BaseResponse<List<AgentResponse>>> {
        return transaction {
            val agents = agentRepository.findAll()
            HttpStatusCode.OK to BaseResponse(
                success = true,
                code = HttpStatusCode.OK.value,
                data = agents.toResponses()
            )
        }
    }

    /**
     * Update agent
     */
    fun update(
        id: Int,
        name: String?,
        username: String?,
        password: String?
    ): Pair<HttpStatusCode, BaseResponse<AgentResponse>> {
        return transaction {
            try {
                val agent = agentRepository.update(
                    id = id,
                    name = name,
                    username = username,
                    password = password
                )

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Agent updated successfully",
                    data = agent.toResponse()
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to update agent",
                    data = null
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = "Internal server error",
                    data = null
                )
            }
        }
    }

    /**
     * Delete agent
     */
    fun delete(id: Int): Pair<HttpStatusCode, BaseResponse<Unit>> {
        return transaction {
            try {
                agentRepository.delete(id)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Agent deleted successfully",
                    data = null
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.NotFound to BaseResponse(
                    success = false,
                    code = HttpStatusCode.NotFound.value,
                    message = e.message ?: "Agent not found",
                    data = null
                )
            } catch (e: Exception) {
                HttpStatusCode.InternalServerError to BaseResponse(
                    success = false,
                    code = HttpStatusCode.InternalServerError.value,
                    message = "Internal server error",
                    data = null
                )
            }
        }
    }

}

// ==================== Request/Response Models ====================

/**
 * Request to create a new agent
 */
@Serializable
data class CreateAgentRequest(
    val name: String,
    val username: String,
    val password: String
)

/**
 * Request to update an agent
 */
@Serializable
data class UpdateAgentRequest(
    val name: String?,
    val username: String?,
    val password: String?
)

/**
 * Agent response
 */
@Serializable
data class AgentResponse @OptIn(ExperimentalTime::class) constructor(
    val id: Int,
    val name: String,
    val username: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
