@file:OptIn(ExperimentalTime::class)

package service

import io.ktor.http.*
import model.response.PointResponse
import model.response.base.BaseResponse
import model.response.base.PaginatedResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import repository.PointRepository
import kotlin.time.ExperimentalTime

class PointService(
    private val pointRepository: PointRepository,
) {
    fun getPointLedgerByClientId(
        clientId: Int,
        page: Int,
        pageSize: Int
    ): Pair<HttpStatusCode, BaseResponse<PaginatedResponse<PointResponse>>> {
        return transaction {
            try {
                val validPage = if (page >= 1) page else 1
                val validPageSize = if (pageSize >= 1) pageSize else 20

                val points = pointRepository.findByClientPaginated(clientId, validPage, validPageSize)
                val total = pointRepository.totalCountByClient(clientId)
                val totalPages = if (total == 0) 1 else ((total + validPageSize - 1) / validPageSize)

                val responses = points.map { point ->
                    PointResponse(
                        id = point.id.value,
                        amount = pointRepository.run { point.decryptedAmount() },
                        createdAt = point.createdAt,
                        submissionId = point.submission?.id?.value
                    )
                }

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Point ledger retrieved successfully",
                    data = PaginatedResponse(
                        data = responses,
                        page = validPage,
                        pageSize = validPageSize,
                        total = total,
                        totalPages = totalPages
                    )
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
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

    fun claimPointsByClientId(
        clientId: Int,
        amount: Int
    ): Pair<HttpStatusCode, BaseResponse<PointResponse>> {
        return transaction {
            try {
                if (amount <= 0) {
                    throw IllegalArgumentException("Amount must be greater than zero")
                }
                val point = pointRepository.create(clientId, -amount, null)
                val response = PointResponse(
                    id = point.id.value,
                    amount = pointRepository.run { point.decryptedAmount() },
                    createdAt = point.createdAt,
                    submissionId = point.submission?.id?.value
                )
                HttpStatusCode.Created to BaseResponse(
                    success = true,
                    code = HttpStatusCode.Created.value,
                    message = "Points deducted successfully",
                    data = response
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Invalid request",
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