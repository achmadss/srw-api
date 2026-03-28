package config

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import model.request.*
import resource.AdminResource
import resource.AgentResource
import resource.ClientResource

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        // Auth validations
        validate<BasicAuthRequest> { request ->
            when {
                request.username.isBlank() -> ValidationResult.Invalid("Username cannot be blank")
                request.password.isBlank() -> ValidationResult.Invalid("Password cannot be blank")
                else -> ValidationResult.Valid
            }
        }
        validate<NfcAuthRequest> { request ->
            when {
                request.nfc.isBlank() -> ValidationResult.Invalid("NFC cannot be blank")
                else -> ValidationResult.Valid
            }
        }
        validate<RefreshTokenAuthRequest> { request ->
            when {
                request.refreshToken.isBlank() -> ValidationResult.Invalid("Refresh token cannot be blank")
                else -> ValidationResult.Valid
            }
        }

        // Client validations
        validate<AdminResource.Clients> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<AdminResource.Agents> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<AdminResource.Points.ClientPoints> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<AdminResource.Submissions> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<AgentResource.Submissions> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<ClientResource.Submissions> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<ClientResource.Profile.Points> { resource ->
            when {
                resource.page < 1 -> ValidationResult.Invalid("Page must be greater than or equal to 1")
                resource.pageSize < 1 -> ValidationResult.Invalid("Page size must be greater than or equal to 1")
                else -> ValidationResult.Valid
            }
        }
        validate<CreateClientRequest> { request ->
            when {
                request.nfc.isBlank() -> ValidationResult.Invalid("NFC cannot be blank")
                request.name.isBlank() -> ValidationResult.Invalid("Name cannot be blank")
                request.address.isBlank() -> ValidationResult.Invalid("Address cannot be blank")
                else -> ValidationResult.Valid
            }
        }

        // Point validations
        validate<ClaimPointsRequest> { request ->
            when {
                request.amount <= 0 -> ValidationResult.Invalid("Amount must be greater than 0")
                else -> ValidationResult.Valid
            }
        }

        // Submission validations
        validate<ReviewSubmissionRequest> { request ->
            when {
                !request.approved && request.rejectionReason.isNullOrBlank() -> ValidationResult.Invalid("Rejection reason is required when rejecting a submission")
                else -> ValidationResult.Valid
            }
        }
        validate<AssignAgentRequest> { request ->
            when {
                request.agentId <= 0 -> ValidationResult.Invalid("Agent ID must be greater than 0")
                else -> ValidationResult.Valid
            }
        }
        validate<ManualMetadataRequest> { request ->
            when {
                request.metadata.isEmpty() -> ValidationResult.Invalid("Metadata list cannot be empty")
                else -> {
                    // Validate each metadata item
                    val invalidItem = request.metadata.firstOrNull { item ->
                        item.trashType.isBlank() || item.amount <= 0
                    }
                    if (invalidItem != null) {
                        ValidationResult.Invalid("Each metadata item must have a non-blank trash type name and amount greater than 0")
                    } else {
                        ValidationResult.Valid
                    }
                }
            }
        }

        // Agent validations
        validate<CreateAgentRequest> { request ->
            when {
                request.name.isBlank() -> ValidationResult.Invalid("Name cannot be blank")
                request.username.isBlank() -> ValidationResult.Invalid("Username cannot be blank")
                request.password.isBlank() -> ValidationResult.Invalid("Password cannot be blank")
                else -> ValidationResult.Valid
            }
        }
        validate<UpdateAgentRequest> { request ->
            when {
                request.name?.isBlank() == true -> ValidationResult.Invalid("Name cannot be blank")
                request.username?.isBlank() == true -> ValidationResult.Invalid("Username cannot be blank")
                else -> ValidationResult.Valid
            }
        }

        // Trash validations
        validate<CreateTrashRequest> { request ->
            when {
                request.name.isBlank() -> ValidationResult.Invalid("Name cannot be blank")
                request.pointsPerUnit <= 0 -> ValidationResult.Invalid("Points per unit must be greater than 0")
                else -> ValidationResult.Valid
            }
        }
        validate<UpdateTrashRequest> { request ->
            when {
                request.pointsPerUnit != null && request.pointsPerUnit <= 0 -> ValidationResult.Invalid("Points per unit must be greater than 0")
                else -> ValidationResult.Valid
            }
        }
    }
}

