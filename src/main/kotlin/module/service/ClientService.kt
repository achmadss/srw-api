package module.service

import io.ktor.http.HttpStatusCode
import module.model.toResponse
import resource.client.ClientResponse
import module.model.response.BaseResponse
import module.model.response.PaginatedResponse
import module.repository.ClientRepository
import module.repository.PointRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ClientService(
    private val clientRepository: ClientRepository,
    private val pointRepository: PointRepository
) {
    fun create(
        name: String,
        nfc: String,
        address: String,
    ): Pair<HttpStatusCode, BaseResponse<ClientResponse>> {
        return transaction {
            try {
                val client = clientRepository.create(
                    name = name,
                    nfc = nfc,
                    address = address
                )

                // Calculate total points for the client (new client has 0 points)
                val totalPoints = pointRepository.findByClient(client.id.value)
                    .sumOf { it.amount }

                HttpStatusCode.Created to BaseResponse(
                    success = true,
                    code = HttpStatusCode.Created.value,
                    message = "Client created successfully",
                    data = client.toResponse(totalPoints)
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to create client",
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

    fun getPaginated(
        page: Int,
        pageSize: Int
    ): Pair<HttpStatusCode, BaseResponse<PaginatedResponse<ClientResponse>>> {
        return transaction {
            val page = if (page >= 1) page else 1
            val pageSize = if (pageSize >= 1) pageSize else 20
            val total = clientRepository.totalCount()
            val totalPages = if (total == 0) 1 else ((total + pageSize - 1) / pageSize)
            val paginated = clientRepository.findAllPaginated(page, pageSize)

            HttpStatusCode.OK to BaseResponse(
                success = true,
                code = HttpStatusCode.OK.value,
                data = PaginatedResponse(
                    data = paginated.map { client ->
                        // Calculate total points for each client
                        val totalPoints = pointRepository.findByClient(client.id.value)
                            .sumOf { it.amount }

                        client.toResponse(totalPoints)
                    },
                    page = page,
                    pageSize = pageSize,
                    total = total,
                    totalPages = totalPages
                )
            )
        }
    }

    fun getByNfc(nfc: String): Pair<HttpStatusCode, BaseResponse<ClientResponse?>> {
        return transaction {
            val client = clientRepository.findByNfc(nfc)
            if (client != null) {
                // Calculate total points for the client
                val totalPoints = pointRepository.findByClient(client.id.value)
                    .sumOf { it.amount }

                return@transaction HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = client.toResponse(totalPoints)
                )
            }
            return@transaction HttpStatusCode.BadRequest to BaseResponse(
                success = false,
                code = HttpStatusCode.BadRequest.value,
                message = "Client with NFC $nfc was not found",
                data = null,
            )
        }
    }

}