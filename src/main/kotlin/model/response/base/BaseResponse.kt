package model.response.base

import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String = "",
    val data: T?
)
