package service

import io.ktor.http.*
import model.response.AgentResponse
import model.response.base.BaseResponse
import model.response.base.PaginatedResponse
import model.toAgentResponse
import model.toAgentResponses
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.AgentRepository

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
                    data = agent.toAgentResponse()
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
                    data = agent.toAgentResponse()
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
                    data = paginatedAgents.toAgentResponses(),
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
                data = agents.toAgentResponses()
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
                    data = agent.toAgentResponse()
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