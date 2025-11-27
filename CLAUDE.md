# Claude Code Assistant - Project Guidelines

This document contains naming conventions and project preferences for the SRW API project.

## Naming Conventions

### API Models
- **Use Request/Response naming** instead of Dto
  - ✅ `CreateSubmissionRequest`
  - ✅ `SubmissionResponse`
  - ✅ `ReviewSubmissionRequest`
  - ❌ `SubmissionDto`
  - ❌ `CreateSubmissionDto`

### Consistency Rule
- Once a naming pattern is chosen, apply it consistently across the entire codebase
- If using `CreateSubmissionRequest`, then use `UpdateSubmissionRequest`, `AssignAgentRequest`, etc.
- Do not mix naming conventions (e.g., don't use both `Request` and `Dto` suffixes)

## Architecture Patterns

### Technology Stack
- **Framework:** Ktor 3.3.2
- **Language:** Kotlin 2.2.20
- **ORM:** Exposed 1.0.0-rc-3
- **Database:** PostgreSQL
- **DI:** Koin 4.1.1
- **Authentication:** JWT (separate secrets for Admin, Client, Agent)
- **Serialization:** kotlinx.serialization

### Project Structure
```
src/main/kotlin/
├── module/v1/
│   ├── model/           # Database entities and tables
│   ├── repository/      # Data access layer
│   ├── service/         # Business logic layer
│   └── resource/           # API routes and resources
│       ├── auth/
│       ├── client/
│       ├── agent/
│       └── submission/
```

### Routing
- **Type-Safe Routing:** Use Ktor Resources with `@Resource` annotations
- **Authentication:** Use proper JWT authentication per resource (ADMIN, CLIENT, AGENT)
- **Request Validation:** Install RequestValidation per resource

Example:
```kotlin
@Resource("/submissions")
class SubmissionResource(
    val page: Int = 1,
    val pageSize: Int = 20,
) {
    @Resource("{id}")
    class ById(val parent: SubmissionResource = SubmissionResource(), val id: Int)
}
```

### Service Layer Pattern
- Services return `Pair<HttpStatusCode, BaseResponse<T>>`
- Services contain business logic and validation
- Services use repositories for data access
- Example: `ClientService.kt`

### Repository Pattern
- Repositories wrap database operations with `transaction { }`
- Use Exposed DAO pattern
- Example: `ClientRepository.kt`

## User Roles & Authentication

### Three User Types
1. **Admin** (`JwtAuth.ADMIN`)
   - Full access to all resources
   - Can review submissions
   - Can assign agents

2. **Client** (`JwtAuth.CLIENT`)
   - Limited to client resources
   - Can create submissions
   - Can view own submissions

3. **Agent** (`JwtAuth.AGENT`)
   - Limited to agent resources
   - Can view assigned submissions
   - Can confirm pickups

## Response Format

### Standard Response
```kotlin
@Serializable
data class BaseResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String = "",
    val data: T?
)
```

### Paginated Response
```kotlin
@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int
)
```

## ML Integration

### Command Line Execution
- ML program is a separate Python application
- Backend calls ML program via command line execution
- Backend waits for output before proceeding
- ML program outputs metadata for each image

## Submission Workflow

### Status Flow
1. `PENDING` - Client uploaded images
2. `ML_PROCESSING` - ML program processing images
3. `AWAITING_REVIEW` - All images processed, waiting for admin review
4. `APPROVED` - Admin approved submission
5. `REJECTED` - Admin rejected submission
6. `ASSIGNED` - Agent assigned by admin (only after approval)
7. `PICKED_UP` - Agent confirmed pickup
8. `COMPLETED` - Final state

### Key Features
- Agent field is nullable (assigned only after admin approval)
- Track rejection reason, admin notes, pickup location
- Calculate and store total points
- Maintain audit trail of all status changes
- Timestamps for each major state transition

## Database Schema Management
- Using Exposed ORM's `SchemaUtils.create()`
- No separate migration files
- Schema auto-created on application start
- Tables registered in `Config.kt`

## Configuration
- Environment variables loaded from `.env` or system environment
- JWT secrets per user type (admin, client, agent)
- Database connection via Koin DI

## Code Style Preferences
- Use Kotlin idioms and conventions
- Prefer immutability where possible
- Use data classes for DTOs/Requests/Responses
- Use enum classes for fixed value sets
- Proper error handling with HttpStatusCode
