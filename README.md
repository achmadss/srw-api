# Smart Recycle Waste (SRW) API

A waste management system that leverages machine learning to classify trash types and reward users for recycling. Built with Ktor, the system handles image submissions, ML-based trash classification, admin review, and agent pickup coordination.

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Quick Start](#quick-start)
- [Submission Workflow](#submission-workflow)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Environment Configuration](#environment-configuration)
- [Development Guide](#development-guide)

## Overview

The SRW system enables clients to:
1. Upload images of recyclable waste via NFC authentication
2. Receive ML-powered trash classification
3. Earn points based on trash type and quantity
4. Coordinate pickup with assigned agents

The system serves three user roles:
- **Clients**: Upload waste images and track points
- **Admins**: Review submissions, assign agents, manage system
- **Agents**: View assigned pickups and confirm collection

## Technology Stack

- **Framework**: Ktor 3.3.2
- **Language**: Kotlin 2.2.20
- **Database**: PostgreSQL 16
- **ORM**: Exposed 1.0.0-rc-3
- **Object Storage**: MinIO
- **Message Queue**: RabbitMQ 3
- **DI**: Koin 4.1.1
- **Authentication**: JWT (separate secrets per role)
- **ML Service**: Python 3 worker with simulated processing

## System Architecture

```
┌─────────────┐      ┌─────────────┐      ┌──────────────┐
│   Client    │──────│  SRW API    │──────│  PostgreSQL  │
│   (NFC)     │      │  (Ktor)     │      │              │
└─────────────┘      └─────────────┘      └──────────────┘
                            │
                     ┌──────┴──────┐
                     │             │
              ┌──────▼─────┐ ┌────▼─────┐
              │   MinIO    │ │ RabbitMQ │
              │  Storage   │ │  Broker  │
              └────────────┘ └────┬─────┘
                                  │
                            ┌─────▼──────┐
                            │ ML Service │
                            │  (Python)  │
                            └────────────┘
```

**ML Processing Flow**:
1. Client uploads images → Stored in MinIO
2. API publishes ML job to RabbitMQ queue
3. ML worker processes images
4. ML worker publishes results to results queue
5. API consumes results and creates metadata records
6. Submission moves to AWAITING_REVIEW status

## Quick Start

### Using Docker Compose (Recommended)

1. Copy and configure environment:
   ```bash
   cp .env.example .env
   # Edit .env if needed (defaults work for development)
   ```

2. Start all services:
   ```bash
   docker-compose up -d
   ```

    This starts:
    - PostgreSQL (port 5432)
    - MinIO (API: 9000, Console: 9001)
    - RabbitMQ (AMQP: 5672, Management: 15672)
    - ML Service (Python worker)
    - SRW API (port 8080)

3. View logs:
   ```bash
   docker-compose logs -f api      # API logs
   docker-compose logs -f ml-service  # ML worker logs
   ```

4. Access services:
   - API: http://localhost:8080
   - API Health Check: http://localhost:8080/health
   - API Documentation (Swagger UI): http://localhost:8080/swagger
   - MinIO Console: http://localhost:9001 (minioadmin/minioadmin)
   - RabbitMQ Management: http://localhost:15672 (admin/admin)

5. Stop services:
   ```bash
   docker-compose down
   ```

### Using Gradle (Local Development)

Requires PostgreSQL, MinIO, and RabbitMQ running locally or via Docker:

```bash
# Start only infrastructure services
docker-compose up -d postgres minio rabbitmq ml-service

# Run API locally
./gradlew run
```

**Available Gradle tasks**:
- `./gradlew test` - Run tests
- `./gradlew build` - Build project
- `./gradlew buildFatJar` - Build executable JAR
- `./gradlew run` - Run server

## Submission Workflow

```
┌────────────┐
│  PENDING   │ ← Client uploads images
└─────┬──────┘
      │
      ▼
┌────────────────┐
│ ML_PROCESSING  │ ← ML worker processes images
└────────┬───────┘
         │
         ▼
┌─────────────────┐
│ AWAITING_REVIEW │ ← Admin reviews results
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌──────┐  ┌──────────┐
│REJECT│  │ APPROVED │
└──────┘  └────┬─────┘
               │
               ▼
         ┌──────────┐
         │ ASSIGNED │ ← Admin assigns agent
         └────┬─────┘
              │
              ▼
        ┌───────────┐
        │ PICKED_UP │ ← Agent confirms pickup
        └─────┬─────┘
              │
              ▼
        ┌───────────┐
        │ COMPLETED │
        └───────────┘
```

**Status Descriptions**:
- `PENDING` - Images uploaded, awaiting ML processing
- `ML_PROCESSING` - ML worker processing images
- `AWAITING_REVIEW` - ML complete, awaiting admin review
- `APPROVED` - Admin approved submission
- `REJECTED` - Admin rejected (terminal state)
- `ASSIGNED` - Agent assigned for pickup
- `PICKED_UP` - Agent confirmed pickup
- `COMPLETED` - Workflow complete (terminal state)

## API Endpoints

Base URL: `http://localhost:8080`

### Authentication Endpoints

- `GET /health` - Health check endpoint

- `POST /auth/login/admin` - Admin login with username/password
  ```json
  {"username": "admin", "password": "admin"}
  ```

- `POST /auth/login/agent` - Agent login with username/password
  ```json
  {"username": "agent1", "password": "password"}
  ```

- `POST /auth/login/client` - Client login with NFC card
  ```json
  {"nfc": "NFC123456"}
  ```

- `POST /auth/logout` - Logout (revoke refresh token)
  ```json
  {"refreshToken": "..."}
  ```

- `POST /auth/refresh` - Refresh access token
  ```json
  {"refreshToken": "..."}
  ```

**Token Details**:
- Access tokens: 15 minutes expiry
- Refresh tokens: 30 days expiry, stored in DB

### Admin-Only Endpoints

**Client Management:**
- `GET /admin/clients` - Get paginated list of clients
- `POST /admin/clients/new` - Create new client (address is optional)
  ```json
  {
    "name": "John Doe",
    "nfc": "NFC123456",
    "address": "123 Main St",
    "latitude": -6.200000,
    "longitude": 106.816666
  }
  ```

**Agent Management:**
- `GET /admin/agents` - Get paginated list of agents
- `POST /admin/agents/new` - Create new agent
  ```json
  {
    "name": "Agent Smith",
    "username": "agent1",
    "password": "password123"
  }
  ```
- `GET /admin/agents/{id}` - Get agent details
- `PUT /admin/agents/{id}` - Update agent
- `DELETE /admin/agents/{id}` - Delete agent

**Points Management:**
- `GET /admin/points/{clientId}` - Get client points ledger
- `POST /admin/points/{clientId}/claim` - Claim points for client (deduct from balance)

**Trash Types Management:**
- `GET /admin/trash` - Get all trash types
- `POST /admin/trash/new` - Create new trash type
  ```json
  {
    "name": "plastic",
    "pointsPerUnit": 10
  }
  ```
- `GET /admin/trash/{name}` - Get trash type by name
- `PUT /admin/trash/{name}` - Update trash type points
- `DELETE /admin/trash/{name}` - Delete trash type

**Submission Management:**
- `GET /admin/submissions` - Get all submissions (paginated)
- `GET /admin/submissions/{id}` - Get submission details
- `POST /admin/submissions/{id}/review` - Review submission (approve/reject)
  ```json
  {
    "approved": true,
    "rejectionReason": null,
    "adminNotes": "Looks good"
  }
  ```
- `POST /admin/submissions/{id}/assign` - Assign agent to submission
  ```json
  {
    "agentId": 1
  }
  ```
- `GET /admin/submissions/{id}/history` - Get submission status history
- `GET /admin/submissions/{id}/ml-status` - Get ML processing status
- `POST /admin/submissions/{id}/images/{imageId}/metadata` - Manually update image metadata
  ```json
  {
    "metadata": [
      {"trashType": "plastic", "amount": 5},
      {"trashType": "metal", "amount": 2}
    ]
  }
  ```

### Client-Only Endpoints

- `GET /clients/profile` - Get client profile
- `POST /clients/profile/address` - Set/update client address and location (for first-time setup)
- `GET /clients/submissions` - Get client's submissions (paginated)
- `POST /clients/submissions/new` - Create new submission with images (multipart/form-data)
  - Accepts: `image` (files) only; address/latlong fetched from client's stored profile
- `GET /clients/submissions/{id}` - Get submission details (own submissions only)
- `GET /clients/profile/points` - Get client's points ledger

### Agent-Only Endpoints

- `GET /agents/submissions` - Get assigned submissions (paginated)
- `GET /agents/submissions/{id}` - Get submission details (assigned only)
- `POST /agents/submissions/{id}/pickup` - Confirm pickup of assigned submission
  ```json
  {
    "notes": "Picked up at 10:00 AM"
  }
  ```

## API Documentation

The API provides comprehensive documentation through multiple channels:

### Swagger UI
- **URL**: `http://localhost:8080/swagger`
- **Features**: Interactive API documentation with request/response examples
- **Authentication**: Use the "Authorize" button with JWT tokens

### OpenAPI Specification
- **File**: `src/main/resources/openapi/documentation.yaml`
- **Format**: OpenAPI 3.0.3 compliant
- **Usage**: Import into tools like Postman, Insomnia, or generate client SDKs

### Health Check
- **Endpoint**: `GET /health`
- **Response**: `{"status": "healthy"}`
- **Purpose**: Service health monitoring and load balancer checks

## Database Schema

### Tables

**admins**
- id, username, password (hashed), createdAt, updatedAt

**agents**
- id, name, username, password (hashed), createdAt, updatedAt

**clients**
- nfc (PK), name, address (nullable), latitude (nullable), longitude (nullable), createdAt, updatedAt

**submissions**
- id, clientId (FK), agentId (FK, nullable), status
- rejectionReason, adminNotes
- submissionAddress, submissionLatitude, submissionLongitude (captured at submission time)
- totalPoints, createdAt, updatedAt, processedAt, reviewedAt, assignedAt, pickedUpAt

**images**
- id (UUID string, PK), submissionId (FK)
- mlStatus, mlError
- createdAt, updatedAt

**Note**: Image URLs are generated dynamically at request time using presigned URLs with the `MINIO_PUBLIC_ENDPOINT` configuration. The `images` table no longer stores full URLs.

**metadata**
- id, amount, imageId (FK), trashName (FK)
- createdAt, updatedAt

**trash**
- name (PK), pointsPerUnit, createdAt, updatedAt

**points**
- id, clientNfc (FK), submissionId (FK)
- points, description, createdAt

**submission_history**
- id, submissionId (FK), status, notes, timestamp

**refresh_tokens**
- id, token (unique), userId, userType
- expiresAt, createdAt

### Relationships

```
Client 1──▶ N Submission
Agent 1──▶ N Submission (nullable)
Submission 1──▶ N Image
Submission 1──▶ N Point
Submission 1──▶ N SubmissionHistory
Image 1──▶ N Metadata
Trash 1──▶ N Metadata
```

## Environment Configuration

### Required Environment Variables

The application requires these environment variables to start (see `Constants.kt:64-81`):

**Database** (3 variables)
- `DB_URL` - Database connection URL (e.g., `jdbc:postgresql://localhost:5432/srw_db`)
- `DB_USER` - Database username
- `DB_PASSWORD` - Database password

**JWT Configuration** (7 variables)
- `JWT_ISSUER` - Token issuer URL
- `ADMIN_JWT_AUDIENCE` - Admin token audience
- `CLIENT_JWT_AUDIENCE` - Client token audience
- `AGENT_JWT_AUDIENCE` - Agent token audience
- `ADMIN_JWT_SECRET` - Admin signing secret (**change in production!**)
- `CLIENT_JWT_SECRET` - Client signing secret (**change in production!**)
- `AGENT_JWT_SECRET` - Agent signing secret (**change in production!**)

**MinIO Object Storage** (5 variables)
- `MINIO_HOSTNAME` - Internal MinIO server hostname (for API-to-MinIO communication, e.g., `http://minio:9000`)
- `MINIO_PUBLIC_HOSTNAME` - Public-facing MinIO hostname (for generating presigned URLs in API responses, e.g., `https://srw-assets.achmad.dev`)
- `MINIO_ACCESS_KEY` - MinIO username
- `MINIO_SECRET_KEY` - MinIO password
- `MINIO_BUCKET` - Bucket name for images

**RabbitMQ** (1 variable)
- `RABBITMQ_URL` - Message broker URL (e.g., `amqp://admin:admin@localhost:5672`)

### Optional Environment Variables

These have defaults if not set:

- `DEFAULT_ADMIN_USERNAME` - Default admin username (default: `admin`)
- `DEFAULT_ADMIN_PASSWORD` - Default admin password (default: random 16-char, logged on first run)
- `TRASH_TYPES_CONFIG_PATH` - Path to trash types config (default: `trash-types.json`)
- `DEFAULT_TRASH_POINTS_PER_UNIT` - Default points per unit (default: `10`)

### Setup

**For Docker Compose**: All required variables are already configured in `docker-compose.yml` with sensible defaults. Just run:
```bash
docker-compose up -d
```

**For Local Development**: Copy and configure `.env`:
```bash
cp .env.example .env
# Edit .env with your local settings (use localhost for services)
./gradlew run
```

**Security Warning**: Change JWT secrets in production! Generate with:
```bash
openssl rand -base64 32
```

## Development Guide

### Project Structure

```
├── src/main/kotlin/                # Main Kotlin application
│   ├── Application.kt              # Main entry point
│   ├── Config.kt                   # Application configuration
│   ├── Constants.kt                # Environment constants
│   ├── module/                     
│   │   ├── model/                  # Database entities (Exposed DAO)
│   │   ├── repository/             # Data access layer
│   │   ├── service/                # Business logic layer
│   │   └── resource/               # API endpoints (Ktor routing)
│   └── util/                       # Utilities
├── src/main/resources/             # Application resources
│   ├── logback.xml                 # Logging configuration
│   └── openapi/                    # OpenAPI specification
│       └── documentation.yaml      # API documentation
├── ml-service/                     # Python ML worker service
│   ├── worker.py                   # RabbitMQ consumer & ML processing
│   ├── config_loader.py            # Configuration loader
│   ├── requirements.txt            # Python dependencies
│   └── Dockerfile                  # ML service container
├── docker-compose.yml              # Multi-service orchestration
├── Dockerfile                      # Main API container
├── build.gradle.kts                # Kotlin build configuration
├── settings.gradle.kts             # Gradle settings
├── gradle.properties               # Gradle properties
├── trash-types.json                # ML model configuration
├── .env.example                    # Environment variables template
└── README.md                       # This file
```

### Adding New Trash Types

Edit `trash-types.json`:

```json
{
  "version": "1.0.0",
  "lastUpdated": "2025-11-26T00:00:00Z",
  "trashTypes": [
    {"name": "plastic"},
    {"name": "metal"},
    {"name": "glass"},
    {"name": "paper"},
    {"name": "organic"}
  ],
  "mlMappings": {
    "plastic_bottle": "plastic",
    "coca_cola_bottle": "plastic",
    "pepsi_bottle": "plastic",
    "water_bottle": "plastic",
    "soda_bottle": "plastic",
    "aluminum_can": "metal",
    "soda_can": "metal",
    "metal_can": "metal",
    "tin_can": "metal",
    "steel_can": "metal",
    "glass_bottle": "glass",
    "wine_bottle": "glass",
    "beer_bottle": "glass",
    "cardboard": "paper",
    "newspaper": "paper",
    "magazine": "paper",
    "food_waste": "organic",
    "fruit": "organic",
    "vegetable": "organic"
  }
}
```

- `trashTypes`: Define recyclable categories with default points per unit (configurable via `DEFAULT_TRASH_POINTS_PER_UNIT`)
- `mlMappings`: Map ML model outputs to trash types (expand as you discover new model outputs)
- Restart services to apply changes

### ML Worker Behavior

Current implementation (`ml-service/worker.py`):
- Simulates ML processing (2-5 seconds per image)
- 10% random failure rate for testing
- Generates 1-3 random trash types per image
- Maps outputs using `trash-types.json`

To implement real ML:
1. Replace `process_image()` function
2. Integrate actual ML model (TensorFlow, PyTorch, etc.)
3. Update `mlMappings` in config as you discover model outputs

### Points Calculation

Formula: `totalPoints = Σ(metadata.amount × trash.pointsPerUnit)`

Example:
- Image has 5 plastic bottles (10 pts/unit) = 50 points
- Image has 2 metal cans (15 pts/unit) = 30 points
- Total: 80 points awarded to client

### Schema Management

Using Exposed ORM's schema auto-creation:
- No migration files needed
- Schema created on application start
- Tables registered in `Config.kt`

**Warning**: Not recommended for production. Use migration tools (Flyway/Liquibase) for production deployments.

### Authentication Flow

1. User logs in → Receive access token (15 min) + refresh token (30 days)
2. Use access token for API requests (header: `Authorization: Bearer {token}`)
3. Token expired → Call `/auth/refresh` with refresh token
4. New token pair issued, old refresh token revoked
5. Logout → Refresh token revoked in database

### Monitoring and Logging

The application provides comprehensive monitoring capabilities:

#### Health Checks
- **Endpoint**: `GET /health`
- **Purpose**: Service availability monitoring
- **Response**: `{"status": "healthy"}`

#### Docker Health Checks
- PostgreSQL, MinIO, and RabbitMQ include health checks
- API service has health check endpoint for load balancers
- All services restart automatically on failure (unless-stopped policy)

#### Logging
- **Framework**: Logback with SLF4J
- **Configuration**: `src/main/resources/logback.xml`
- **Levels**: INFO (default), DEBUG (development)
- **Outputs**: Console and file (configurable)

#### Key Monitoring Points
- Database connection health
- RabbitMQ message processing
- MinIO storage operations
- JWT token validation
- ML job queue status

### Testing the Workflow

1. Create client (address is optional, can be set later via `/clients/profile/address`):
   ```bash
   POST /admin/clients/new
   {"name": "Test User", "nfc": "TEST001", "address": "123 Test St", "latitude": -6.2, "longitude": 106.8}
   ```

2. Login as client:
   ```bash
   POST /auth/login/client
   {"nfc": "TEST001"}
   ```

3. Upload images (multipart):
   ```bash
   POST /clients/submissions/new
   # Upload 1-3 images
   # Address/latlong automatically from client's stored profile
   ```

4. Admin reviews (login as admin first):
   ```bash
   POST /admin/submissions/{id}/review
   {"approved": true, "adminNotes": "Good submission"}
   ```

5. Admin assigns agent:
   ```bash
   POST /admin/submissions/{id}/assign
   {"agentId": 1}
   ```

6. Agent confirms pickup:
   ```bash
   POST /agents/submissions/{id}/pickup
   {"notes": "Picked up successfully"}
   ```

7. Check client points:
   ```bash
   GET /clients/submissions/points
   # Response includes points ledger
   ```

## Resources

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Exposed ORM Guide](https://github.com/JetBrains/Exposed/wiki)
- [MinIO Documentation](https://min.io/docs/minio/linux/index.html)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)

## License

Proprietary - All rights reserved