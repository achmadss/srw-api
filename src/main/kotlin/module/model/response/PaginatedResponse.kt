package module.model.response

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int
)
