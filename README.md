# Secure Document Platform

A backend-focused document storage platform built to explore production software engineering concerns beyond basic CRUD: durable storage, authorization, database design, streaming, failure handling, messaging, observability, resilience, and cloud deployment.

The project is intentionally being built in stages. Each milestone introduces a new class of engineering problem while keeping the architecture as simple as the current requirements allow.

---

## Project Goals

The goal is not simply to build a file-upload API.

This project is designed to exercise and demonstrate:

- maintainable backend architecture
- REST API design
- ownership-based authorization
- relational data modeling
- object storage
- streaming large files
- database indexing and transaction behavior
- failure compensation across independent systems
- asynchronous messaging
- idempotency and retry safety
- observability and production debugging
- resilience patterns
- containerization
- AWS deployment and operations

The project favors explicit tradeoffs and incremental evolution over introducing distributed-system complexity before it is needed.

---

## Current Status

**Current milestone: Milestone 1 — Core Document Service**

Milestone 1 is currently in the design phase.

Completed design work includes:

- functional and non-functional requirements
- relational metadata model
- REST API contract
- component boundaries and contracts
- architecture decision records
- upload workflow and failure-handling design

Implementation will follow once the core flows and boundaries are sufficiently defined.

---

# Milestone Roadmap

## Milestone 1 — Core Document Service

### Objective

Build a secure, API-only service for storing private user documents.

### Core Capabilities

An authenticated user can:

- upload one document at a time
- list their documents
- retrieve metadata for one document
- download document content
- permanently delete a document

Documents are private to their owner. Ownership is derived from the authenticated identity rather than accepted from request data.

### Architecture

Milestone 1 uses a deliberately simple architecture:

```text
Client
  |
  v
Spring Boot API
  |
  +---- PostgreSQL
  |
  +---- Private Object Storage
```

Document metadata is stored in PostgreSQL.

Document bytes are stored separately in object storage.

Uploads and downloads pass through the Spring Boot application during this milestone. This is intentionally less scalable than direct object-storage transfers, but it exposes important engineering concerns such as multipart handling, streaming, validation, authorization, and cross-system failure handling.

### Engineering Topics

- REST resource design
- authenticated ownership
- authorization boundaries
- PostgreSQL schema design
- database constraints
- UUIDv7 identifiers
- object storage
- MIME/content inspection
- filename sanitation
- stream-based file handling
- compensation after partial failure
- retry-safe deletion
- unit and integration testing

### Planned Stack

- Java
- Spring Boot
- PostgreSQL
- S3-compatible object storage
- Docker
- JUnit
- Testcontainers

---

## Milestone 2 — Database and Concurrency Depth

### Objective

Move beyond simply using a relational database and develop a deeper understanding of how database behavior affects application correctness and performance.

### Planned Work

- inspect query plans with `EXPLAIN`
- evaluate and refine indexes
- measure query behavior as data volume grows
- add pagination where justified
- explore transaction boundaries
- reproduce concurrency races
- introduce optimistic locking where appropriate
- examine isolation behavior
- test database constraint behavior under concurrent requests

### Engineering Topics

- B-tree indexes
- composite indexes
- query planning
- sargability
- transactions
- isolation
- optimistic concurrency control
- database-backed correctness guarantees

The goal is to understand *why* a database design behaves well, rather than treating PostgreSQL as a persistence black box.

---

## Milestone 3 — Asynchronous Processing and Reliable Messaging

### Objective

Introduce asynchronous work only after the synchronous core service is stable.

### Planned Work

- add RabbitMQ
- publish document-related events
- implement asynchronous consumers
- understand acknowledgements
- implement retry behavior
- build idempotent consumers
- introduce dead-letter handling
- implement the transactional outbox pattern
- deliberately inject failures and duplicate delivery

### Engineering Topics

- asynchronous messaging
- at-least-once delivery
- idempotency
- message acknowledgements
- retries
- dead-letter queues
- transactional outbox
- eventual consistency
- failure recovery

This milestone is intended to make messaging guarantees concrete rather than treating a queue as simply another API call.

---

## Milestone 4 — Networking, Containers, and Observability

### Objective

Make the service diagnosable when something goes wrong outside the application code itself.

### Planned Work

- run dependencies in containers
- investigate container networking
- trace DNS resolution
- examine TCP connectivity
- understand TLS boundaries
- configure connection and request timeouts
- inspect connection pools and thread pools
- add application metrics
- add structured logging
- introduce distributed tracing where useful
- deliberately reproduce dependency and networking failures

### Engineering Topics

- DNS
- TCP
- TLS
- connection pools
- request timeouts
- container networking
- metrics
- logs
- tracing
- production debugging

The emphasis is on being able to diagnose failures systematically rather than restarting components until the problem disappears.

---

## Milestone 5 — Resilience Under Dependency Failure

### Objective

Make the application behave predictably when its dependencies become slow or unavailable.

### Planned Work

- define explicit timeout policies
- implement bounded retries
- add exponential backoff where appropriate
- introduce circuit breaking
- introduce bulkheads / bounded concurrency
- test degraded dependency behavior
- test recovery after dependency restoration

### Engineering Topics

- timeout design
- retry safety
- backoff
- circuit breakers
- bulkheads
- backpressure
- cascading failure prevention

Resilience mechanisms will be added only where failure scenarios justify them.

---

## Milestone 6 — AWS Deployment

### Objective

Deploy and operate the system using managed cloud infrastructure.

### Planned Work

The exact AWS architecture will be chosen when this milestone begins rather than being fixed prematurely.

Expected areas include:

- deploying the Spring Boot service
- managed PostgreSQL
- Amazon S3
- application secrets management
- centralized logging and metrics
- load balancing
- networking and security configuration
- environment-specific configuration
- deployment automation
- production-style operational debugging

### Engineering Topics

- cloud infrastructure
- managed services
- application configuration
- secrets
- networking
- observability
- deployment
- horizontal scaling
- operational tradeoffs

---

# Milestone 1 API

The planned Milestone 1 resource model is:

```text
POST   /documents
GET    /documents
GET    /documents/{documentId}
GET    /documents/{documentId}/content
DELETE /documents/{documentId}
```

Detailed request and response behavior is documented in [`docs/api.md`](docs/api.md).

---

# Milestone 1 Component Model

The current logical component boundaries are:

```text
Request / Response Handler
Authentication Boundary
DocumentService
DocumentValidator
MimeTypeResolver
IdGenerator
DocumentRepository
ObjectStorage
Document
```

`DocumentService` acts as the application workflow coordinator.

Persistence, validation, MIME inspection, ID generation, and object storage remain focused collaborators rather than being embedded directly into the HTTP layer.

Detailed contracts are documented in [`docs/component-contracts.md`](docs/component-contracts.md).

---

# Data Storage

## PostgreSQL

PostgreSQL stores document metadata such as:

- document ID
- owner subject
- filename
- file size
- content type
- upload timestamp

See [`docs/data-model.md`](docs/data-model.md).

## Object Storage

Document bytes are stored separately in private object storage.

The application does not use user-provided filenames as object keys. Storage identity is derived from the internally generated document ID.

---

# Data Flows

Workflow sequencing and partial-failure behavior are documented separately from component contracts.

Current flow documentation includes:

- upload validation order
- PostgreSQL/object-storage write ordering
- upload compensation
- accepted temporary inconsistency
- duplicate-filename race behavior

See [`docs/data-flows.md`](docs/data-flows.md).

---

# Architecture Decisions

Important architectural choices are recorded as Architecture Decision Records (ADRs) rather than being left implicit in the implementation.

Current decisions include:

- [`ADR 0001 — Use UUIDv7 for Document IDs`](docs/decisions/0001-use-uuidv7-for-document-ids.md)
- [`ADR 0002 — Store File Bytes in Object Storage`](docs/decisions/0002-store-files-in-object-storage.md)
- [`ADR 0003 — Proxy File Transfers Through the API in Milestone 1`](docs/decisions/0003-proxy-file-transfers-through-api.md)

Later milestones may intentionally replace some early decisions as the system's requirements change.

---

# Engineering Principles

## Prefer the Simplest Architecture That Meets Current Requirements

The project will not begin as a collection of microservices.

Complexity such as asynchronous workflows, document states, direct-to-S3 uploads, caching, and distributed resilience will be introduced only when a milestone creates a concrete reason for it.

## Use the Database for Invariants

Important data rules should not rely only on application checks when PostgreSQL can enforce them directly.

## Treat Independent Systems as Independent

PostgreSQL and object storage do not share a transaction.

Failure handling must explicitly account for one succeeding while the other fails.

## Make Authorization Explicit

Knowing a document ID is never sufficient to access a document.

Document-specific operations are scoped to both the document ID and authenticated owner.

## Stream Large Content

Document content should move through the application as streams rather than requiring entire files to be materialized as large in-memory byte arrays.

## Design for Failure

Later milestones deliberately introduce dependency failures, duplicate messages, stale state, network problems, and concurrency races.

The goal is not merely to make the happy path work.

---

# Testing Strategy

Testing will grow with the architecture rather than being added at the end.

Planned layers include:

- unit tests for focused business rules
- repository integration tests
- object-storage integration tests
- API integration tests
- Testcontainers-backed infrastructure tests
- concurrency tests in later milestones
- failure-injection tests
- messaging reliability tests
- resilience tests

The project should be able to demonstrate not only that expected behavior works, but also how the system behaves when dependencies fail.

---

# Long-Term Direction

The final system is expected to look substantially different from the Milestone 1 implementation.

That is intentional.

The project is structured so architectural changes are driven by newly introduced requirements:

```text
simple synchronous backend
        ↓
database/concurrency depth
        ↓
asynchronous workflows
        ↓
observability + networking
        ↓
resilience
        ↓
cloud deployment
```

The result should be a system whose architecture can be explained in terms of concrete requirements and tradeoffs rather than a collection of technologies added for their own sake.
