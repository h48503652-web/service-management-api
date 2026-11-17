# Service Management API

![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.0.0](https://img.shields.io/badge/Spring%20Boot-4.0.0-brightgreen)
![Maven Wrapper](https://img.shields.io/badge/Build-Maven%20Wrapper-orange)

A REST API for a small service desk: it manages users, support tickets, ticket assignment, ticket status transitions, and ticket comments. It is built with Java 21 and Spring Boot 4, persists data with Spring Data JPA, and runs against an in-memory H2 database configured for local development.

## Table of contents

- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [REST API](#rest-api)
- [Request and response examples](#request-and-response-examples)
- [Error responses](#error-responses)
- [Getting started on Windows](#getting-started-on-windows)
- [Running the tests](#running-the-tests)
- [Database configuration](#database-configuration)
- [Current limitations](#current-limitations)

## Features

- **Users**: create a user, list all users, and retrieve a user by ID. Email addresses are unique.
- **Tickets**: create, retrieve, update, and delete tickets. Every ticket records the user who created it and may be assigned to a user.
- **Status lifecycle**: tickets start as `OPEN` and move only along the allowed transitions (see [Ticket status lifecycle](#ticket-status-lifecycle)).
- **Assignment**: assign a ticket to any existing user.
- **Comments**: add comments to a ticket and list them page by page, newest first.
- **Filtering, pagination, and sorting**: list tickets by status, priority, or free-text search, with validated `page`, `size`, `sortBy`, and `direction` parameters.
- **Validation**: request bodies are validated with Jakarta Bean Validation.
- **Consistent errors**: a central exception handler returns errors as RFC 9457 problem details in JSON.
- **DTO-based responses**: JPA entities are never serialized directly; controllers return response records.
- **Health check**: `GET /api/health` reports that the service is up.

## Technology stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.0 (Spring Web MVC) |
| Persistence | Spring Data JPA with Hibernate |
| Database | H2, in-memory |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 6 (Jupiter), AssertJ, Spring Boot Test, MockMvc |
| Build | Maven, through the included Maven Wrapper |

## Architecture

The application is a single Spring Boot service with a classic layered design. Each request passes through the layers in one direction:

```text
HTTP client
    │  JSON
    ▼
Controller      Maps HTTP requests and validates request bodies
    ▼
Service         Business rules and transaction boundaries
    ▼
Repository      Spring Data JPA repositories
    ▼
H2 database     In-memory, created at startup
```

- **Controllers** contain no business logic. They accept validated request records and return response records.
- **Services** own the business rules, such as the allowed status transitions and the duplicate-email check. Each service method runs in its own transaction, and read operations use read-only transactions.
- **Entities are mapped to DTOs inside the transaction.** `spring.jpa.open-in-view` is set to `false`, so lazy associations are never loaded while the response is being written.
- **`GlobalExceptionHandler`** translates exceptions into problem-detail JSON responses.

The entry point is `com.example.servicemanagement.ServiceManagementApplication`.

### Domain model

| Entity | Table | Description |
|---|---|---|
| `User` | `users` | A person who creates tickets, is assigned to tickets, or writes comments. |
| `Ticket` | `tickets` | A support request with a title, description, priority, status, creator, and optional assignee. |
| `Comment` | `comments` | A note written by a user on a ticket. Comments are deleted together with their ticket. |

Ticket priorities are `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`.

### Ticket status lifecycle

| Current status | Allowed next statuses |
|---|---|
| `OPEN` | `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `IN_PROGRESS` | `RESOLVED`, `CLOSED` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | None; this is the final status |

Any other transition is rejected with `400 Bad Request`.

## Project structure

```text
.
├── .mvn/wrapper/                  Maven Wrapper configuration
├── src/
│   ├── main/
│   │   ├── java/com/example/servicemanagement/
│   │   │   ├── controller/        REST endpoints
│   │   │   ├── dto/
│   │   │   │   ├── request/       Validated request records
│   │   │   │   └── response/      JSON response records
│   │   │   ├── entity/            JPA entities
│   │   │   ├── enums/             Ticket status and priority
│   │   │   ├── exception/         API exceptions and the central handler
│   │   │   ├── integration/       External notification client
│   │   │   ├── repository/        Spring Data JPA repositories
│   │   │   ├── service/           Transactional business operations
│   │   │   └── ServiceManagementApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/example/servicemanagement/
├── mvnw, mvnw.cmd                 Maven Wrapper scripts
├── pom.xml
└── README.md
```

## REST API

All routes are rooted at `/api`. Request and response bodies are JSON.

| Method | Endpoint | Purpose | Success | Common errors |
|---|---|---|---|---|
| GET | `/api/health` | Health check | 200 | |
| POST | `/api/users` | Create a user | 201 | 400 invalid body, 409 duplicate email |
| GET | `/api/users` | List all users | 200 | |
| GET | `/api/users/{id}` | Retrieve a user | 200 | 404 |
| POST | `/api/tickets` | Create a ticket | 201 | 400 invalid body, 404 unknown user |
| GET | `/api/tickets` | List, filter, and page tickets | 200 | 400 invalid query parameter |
| GET | `/api/tickets/{id}` | Retrieve a ticket | 200 | 404 |
| PUT | `/api/tickets/{id}` | Replace the title, description, priority, and assignee | 200 | 400, 404 |
| PATCH | `/api/tickets/{id}/status?status=IN_PROGRESS` | Change the ticket status | 200 | 400 invalid transition, 404 |
| PATCH | `/api/tickets/{id}/assignment?userId=1` | Assign the ticket to a user | 200 | 404 |
| DELETE | `/api/tickets/{id}` | Delete a ticket and its comments | 204 | 404 |
| POST | `/api/tickets/{id}/comments` | Add a comment | 201 | 400 invalid body, 404 unknown ticket or author |
| GET | `/api/tickets/{id}/comments` | List a ticket's comments, newest first | 200 | 400, 404 |

`PUT /api/tickets/{id}` replaces the assignee as well: leaving out `assignedUserId` removes the current assignment.

### Query parameters for `GET /api/tickets`

| Parameter | Default | Rules |
|---|---|---|
| `page` | `0` | Must be `0` or greater |
| `size` | `10` | Must be between `1` and `100` |
| `sortBy` | `createdAt` | One of `id`, `title`, `status`, `priority`, `createdAt`, `updatedAt` |
| `direction` | `DESC` | `ASC` or `DESC`, case-insensitive |
| `status` | none | Optional filter: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `priority` | none | Optional filter: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `search` | none | Optional case-insensitive match in the title or description |

`GET /api/tickets/{id}/comments` accepts `page` and `size` with the same defaults and rules.

### Request validation rules

| Request | Field | Rule |
|---|---|---|
| Create user | `name` | Required, 2 to 100 characters |
| Create user | `email` | Required, valid email address, unique |
| Create or update ticket | `title` | Required, 3 to 200 characters |
| Create or update ticket | `description` | Required, 10 to 2000 characters |
| Create or update ticket | `priority` | Required |
| Create ticket | `createdByUserId` | Required, must be an existing user |
| Create or update ticket | `assignedUserId` | Optional, must be an existing user |
| Create comment | `content` | Required, up to 2000 characters |
| Create comment | `authorEmail` | Required, must be the email of an existing user |

## Request and response examples

The commands below use `Invoke-RestMethod` in PowerShell against a locally running instance. The responses are shown as the JSON the API returns.

### Create a user

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/users `
  -ContentType 'application/json' `
  -Body '{"name":"Alice Johnson","email":"alice@company.com"}'
```

Response, `201 Created`:

```json
{
  "id": 1,
  "name": "Alice Johnson",
  "email": "alice@company.com",
  "createdAt": "2026-11-17T00:57:11.319044"
}
```

### Create a ticket

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/tickets `
  -ContentType 'application/json' `
  -Body '{"title":"VPN access issue","description":"Remote workers cannot authenticate to the VPN gateway.","priority":"HIGH","createdByUserId":1,"assignedUserId":1}'
```

Response, `201 Created`:

```json
{
  "id": 1,
  "title": "VPN access issue",
  "description": "Remote workers cannot authenticate to the VPN gateway.",
  "status": "OPEN",
  "priority": "HIGH",
  "createdAt": "2026-11-17T00:57:11.914297",
  "updatedAt": "2026-11-17T00:57:11.914297",
  "assignedUser": {
    "id": 1,
    "name": "Alice Johnson",
    "email": "alice@company.com",
    "createdAt": "2026-11-17T00:57:11.319044"
  },
  "createdBy": {
    "id": 1,
    "name": "Alice Johnson",
    "email": "alice@company.com",
    "createdAt": "2026-11-17T00:57:11.319044"
  },
  "commentCount": 0
}
```

### List tickets

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/tickets?page=0&size=10&sortBy=createdAt&direction=DESC&status=OPEN'
```

Response, `200 OK`. Each item in `content` has the same shape as the ticket above:

```json
{
  "content": [
    { "id": 1, "title": "VPN access issue", "status": "OPEN", "priority": "HIGH", "commentCount": 0 }
  ],
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

The response also contains `pageable` and `sort` objects, which are left out here for brevity.

### Change the ticket status

```powershell
Invoke-RestMethod -Method Patch -Uri 'http://localhost:8080/api/tickets/1/status?status=IN_PROGRESS'
```

Response, `200 OK`: the full ticket, with `"status": "IN_PROGRESS"`.

### Assign the ticket

```powershell
Invoke-RestMethod -Method Patch -Uri 'http://localhost:8080/api/tickets/1/assignment?userId=1'
```

Response, `200 OK`: the full ticket, with the new `assignedUser`.

### Add a comment

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/tickets/1/comments `
  -ContentType 'application/json' `
  -Body '{"authorEmail":"alice@company.com","content":"Investigating the access logs now."}'
```

Response, `201 Created`:

```json
{
  "id": 1,
  "ticketId": 1,
  "authorEmail": "alice@company.com",
  "content": "Investigating the access logs now.",
  "createdAt": "2026-11-17T00:57:12.536608"
}
```

## Error responses

Errors are returned as problem details. Validation errors add an `errors` object that maps each invalid field to its message.

```json
{
  "detail": "One or more request fields are invalid",
  "instance": "/api/users",
  "status": 400,
  "title": "Validation failed",
  "timestamp": "2026-11-17T00:57:11.5584648",
  "errors": {
    "name": "Name is required",
    "email": "Email should be valid"
  }
}
```

| Status | `title` | When |
|---|---|---|
| 400 | `Validation failed` | A request body field breaks a validation rule |
| 400 | `Malformed JSON request` | The body is not valid JSON or does not match the expected payload |
| 400 | `Invalid request parameter` | A query parameter has an invalid value, such as `size=101` or `status=UNKNOWN` |
| 400 | `Invalid business state` | A status transition is not allowed |
| 400 | `Bad Request` | A required query parameter is missing |
| 404 | `Resource not found` | The user, ticket, or comment author does not exist |
| 404 | `Not Found` | The URL does not match any endpoint |
| 405 | `Method Not Allowed` | The endpoint does not support the HTTP method; the `Allow` header lists the supported methods |
| 409 | `Duplicate user email` | A user with the same email already exists |
| 415 | `Unsupported Media Type` | A request body is sent with a content type other than `application/json` |
| 500 | `Internal server error` | An unexpected server error; the cause is written to the application log and is not exposed in the response |

## Getting started on Windows

### Requirements

- **JDK 21**, with `JAVA_HOME` pointing to it.
- **Internet access on the first build.** The Maven Wrapper downloads Maven 3.9.11 and the project dependencies. A separate Maven installation is not needed.

### Build and run

Run the commands from the project root in PowerShell.

Run the application directly from the sources:

```powershell
.\mvnw.cmd spring-boot:run
```

Or build the executable JAR and run it:

```powershell
.\mvnw.cmd verify
java -jar target\service-management-api-0.0.1-SNAPSHOT.jar
```

The service listens on `http://localhost:8080`. To confirm that it is running:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

The expected response is `{"status":"UP"}`. Press `Ctrl+C` in the terminal to stop the application.

## Running the tests

```powershell
.\mvnw.cmd test      # compile and run the test suite
.\mvnw.cmd verify    # run the tests and build the executable JAR
```

The tests start the full Spring context against the in-memory database:

| Test class | What it covers |
|---|---|
| `ServiceManagementApiIntegrationTest` | The HTTP layer through MockMvc: users, tickets, status changes, assignment, comments, serialized JSON, and rejected pagination or sorting parameters |
| `TicketUpdatedAtIntegrationTest` | Ticket updates (details, status, assignment) return the same `updatedAt` value that a following `GET` returns |
| `TicketServiceTest` | Service-level rules: ticket creation and assignment, rejected status transitions, and duplicate emails |
| `TicketControllerTest` | Further service-level ticket scenarios |
| `HttpErrorHandlingIntegrationTest` | Error statuses over real HTTP on a random port: unknown URL (404), unsupported method (405), missing parameter (400), unsupported content type (415), and an unexpected failure (500, logged) |
| `HealthControllerTest` | The health endpoint over real HTTP on a random port |
| `ServiceManagementApplicationTests` | The Spring application context loads |

## Database configuration

H2 is configured for local development only. The settings are in `src/main/resources/application.properties`.

| Setting | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:service-management` (PostgreSQL compatibility mode) |
| Username | `sa` |
| Password | empty |
| Schema management | `spring.jpa.hibernate.ddl-auto=update`; Hibernate creates the tables at startup |
| Open Session in View | Disabled (`spring.jpa.open-in-view=false`) |
| Server port | `8080` |

The database lives in memory, so **all data is lost when the application stops**. Each start begins with an empty database.

The H2 web console is not enabled. The project does not include Spring Boot's H2 console module, and `/h2-console` returns `404`.

## Current limitations

- **No authentication or authorization.** Every endpoint is open.
- **Local-development database only.** H2 runs in memory and nothing is persisted between runs. There is no production database configuration.
- **No H2 web console.** The console is not enabled, so the database cannot be browsed from a web page.
- **Page responses are not a stable contract.** Spring Data `Page` objects are serialized directly, and Spring warns that this JSON structure may change between framework versions.
- **Users cannot be updated or deleted**, and the user list is not paginated.
- **The external notification client is not connected.** `ExternalNotificationClient` exists but no ticket workflow calls it.
- **No API documentation endpoint**, such as OpenAPI or Swagger UI.
