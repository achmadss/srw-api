package service

import io.ktor.http.HttpStatusCode
import model.response.TrashResponse
import model.response.base.BaseResponse
import model.toTrashResponse
import repository.TrashRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Service for trash type management operations
 */
class TrashService(
    private val trashRepository: TrashRepository
) {

    /**
     * Create a new trash type
     */
    fun create(
        name: String,
        pointsPerUnit: Int
    ): Pair<HttpStatusCode, BaseResponse<TrashResponse>> {
        return transaction {
            try {
                val trash = trashRepository.create(
                    name = name,
                    pointsPerUnit = pointsPerUnit
                )

                HttpStatusCode.Created to BaseResponse(
                    success = true,
                    code = HttpStatusCode.Created.value,
                    message = "Trash type created successfully",
                    data = trash.toTrashResponse()
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to create trash type",
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
     * Get trash type by name
     */
    fun getByName(name: String): Pair<HttpStatusCode, BaseResponse<TrashResponse?>> {
        return transaction {
            val trash = trashRepository.findByName(name)
            if (trash != null) {
                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    data = trash.toTrashResponse()
                )
            } else {
                HttpStatusCode.NotFound to BaseResponse(
                    success = false,
                    code = HttpStatusCode.NotFound.value,
                    message = "Trash type '$name' not found",
                    data = null
                )
            }
        }
    }

    /**
     * Get all trash types
     */
    fun getAll(): Pair<HttpStatusCode, BaseResponse<List<TrashResponse>>> {
        return transaction {
            val trashTypes = trashRepository.findAll()
            HttpStatusCode.OK to BaseResponse(
                success = true,
                code = HttpStatusCode.OK.value,
                data = trashTypes.map { it.toTrashResponse() }
            )
        }
    }

    /**
     * Update trash type (only pointsPerUnit can be updated, name is primary key)
     */
    fun update(
        name: String,
        pointsPerUnit: Int?
    ): Pair<HttpStatusCode, BaseResponse<TrashResponse>> {
        return transaction {
            try {
                val trash = trashRepository.update(
                    name = name,
                    pointsPerUnit = pointsPerUnit
                )

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Trash type updated successfully",
                    data = trash.toTrashResponse()
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.BadRequest to BaseResponse(
                    success = false,
                    code = HttpStatusCode.BadRequest.value,
                    message = e.message ?: "Failed to update trash type",
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
     * Delete trash type
     */
    fun delete(name: String): Pair<HttpStatusCode, BaseResponse<Unit>> {
        return transaction {
            try {
                trashRepository.delete(name)

                HttpStatusCode.OK to BaseResponse(
                    success = true,
                    code = HttpStatusCode.OK.value,
                    message = "Trash type deleted successfully",
                    data = null
                )
            } catch (e: IllegalArgumentException) {
                HttpStatusCode.NotFound to BaseResponse(
                    success = false,
                    code = HttpStatusCode.NotFound.value,
                    message = e.message ?: "Trash type not found",
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