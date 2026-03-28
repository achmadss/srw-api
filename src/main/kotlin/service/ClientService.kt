package service

import io.ktor.http.*
import model.response.ClientResponse
import model.response.base.BaseResponse
import model.response.base.PaginatedResponse
import model.toClientResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.ClientRepository
import repository.PointRepository
import kotlin.time.Clock

class ClientService(
    private val clientRepository: ClientRepository,
    private val pointRepository: PointRepository
) {
    fun create(
        name: String,
        nfc: String,
        address: String?,
        latitude: Float?,
        longitude: Float?,
    ): Pair<HttpStatusCode, BaseResponse<ClientResponse>> {
        return transaction {
            try {
                val client = clientRepository.create(
                    name = name,
                    nfc = nfc,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )

                HttpStatusCode.Created to BaseResponse(
                    success = true,
                    code = HttpStatusCode.Created.value,
                    message = "Client created successfully",
                    data = client.toClientResponse(0)
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

                        client.toClientResponse(totalPoints)
                    },
                    page = page,
                    pageSize = pageSize,
                    total = total,
                    totalPages = totalPages
                )
            )
        }
    }

    fun getById(id: Int): Pair<HttpStatusCode, BaseResponse<ClientResponse?>> {
        return transaction {
            val client = clientRepository.findById(id)
            if (client != null) {
                val totalPoints = pointRepository.getClientTotalPoints(client.id.value)
                return@transaction HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = client.toClientResponse(totalPoints)
                )
            }
            return@transaction HttpStatusCode.BadRequest to BaseResponse(
                success = false,
                code = HttpStatusCode.BadRequest.value,
                message = "Client with id $id was not found",
                data = null,
            )
        }
    }

    fun getByNfc(nfc: String): Pair<HttpStatusCode, BaseResponse<ClientResponse?>> {
        return transaction {
            val client = clientRepository.findByNfc(nfc)
            if (client != null) {
                // Calculate total points for the client
                val totalPoints = pointRepository.getClientTotalPoints(client.id.value)
                return@transaction HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = client.toClientResponse(totalPoints)
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

    /** 
     * Update client
     */
    fun update(
        id: Int, 
        nfc: String?,
        name: String?,
        address: String?,
        latitude: Float?,
        longitude: Float?,
    ): Pair<HttpStatusCode, BaseResponse<ClientResponse?>> {
        return transaction {
            try {
                val client = clientRepository.update(
                    id = id,
                    name = name,
                    nfc = nfc,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )

                val totalPoints = pointRepository.getClientTotalPoints(client.id.value)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Client updated successfully",
                    data = client.toClientResponse(totalPoints)
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to update client",
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

    fun setAddress(
        id: Int,
        address: String,
        latitude: Float?,
        longitude: Float?,
    ): Pair<HttpStatusCode, BaseResponse<ClientResponse?>> {
        return transaction {
            try {
                val client = clientRepository.findById(id)
                    ?: return@transaction HttpStatusCode.NotFound to BaseResponse(
                        success = false,
                        code = HttpStatusCode.NotFound.value,
                        message = "Client not found",
                        data = null
                    )

                require(address.isNotBlank()) { "Address cannot be blank" }

                client.address = address
                latitude?.let { client.latitude = it }
                longitude?.let { client.longitude = it }
                client.updatedAt = Clock.System.now()

                val totalPoints = pointRepository.getClientTotalPoints(client.id.value)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Address set successfully",
                    data = client.toClientResponse(totalPoints)
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to set address",
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
     * Delete client
     */
    fun delete(id: Int): Pair<HttpStatusCode, BaseResponse<Unit>> {
        return transaction {
            try {
                clientRepository.delete(id)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Client deleted successfully",
                    data = null
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.NotFound to BaseResponse(
                    success = false,
                    code = HttpStatusCode.NotFound.value,
                    message = e.message ?: "Client not found",
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