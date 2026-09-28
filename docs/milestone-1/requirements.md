# Secure Document Platform — Milestone 1 Requirements

## Purpose

Milestone 1 delivers a small, secure, API-only document storage service focused on backend engineering fundamentals: REST APIs, authentication-aware authorization, relational metadata storage, object storage, validation, testing, and failure handling.

## Functional Requirements

- An authenticated user can upload one supported document at a time.
- An authenticated user can list their own documents.
- An authenticated user can retrieve metadata for one document they own.
- An authenticated user can download the content of a document they own.
- An authenticated user can permanently delete a document they own.
- Document ownership is derived from the authenticated identity and is never accepted from client-supplied owner data.
- Users cannot access, download, or delete another user's documents.
- Each stored document includes the following metadata:
  - document ID
  - owner subject
  - filename
  - file size in bytes
  - canonical MIME type
  - upload timestamp
- Duplicate filenames within the same user's document space are rejected.
- Filename uniqueness is case-sensitive.
- Document contents are immutable after upload.
- If document contents change, the changed file is uploaded as a new document.

## Supported File Types

Milestone 1 supports a small allowlist:

- `application/pdf`
- `text/plain`
- `image/jpeg`
- `image/png`

The service must determine file type from file content rather than trusting the client-provided MIME type or filename extension.
The server-detected canonical MIME type is authoritative. If the detected type is supported, a mismatch with the client-provided MIME type or filename extension does not by itself cause rejection; the validated filename is preserved unchanged and the detected canonical MIME type is stored.
If the content type is unsupported or cannot be identified as a supported type, the upload is rejected.

## Non-Functional Requirements

### Durability

- Document metadata and file contents must survive application restarts and redeployments.
- Metadata must not depend on in-memory application state.
- File contents must not depend on the application server's local ephemeral filesystem.

### Security

- Object storage must remain private.
- Clients must not receive object-store credentials.
- Upload, metadata, download, and delete operations require authentication.
- Unauthenticated requests to protected Milestone 1 endpoints return `401 Unauthorized`.
- Resource-level authorization must be enforced for every document-specific operation.
- The application must not reveal whether another user's document ID exists.
- Unauthorized access to a specific document must return the same externally visible result as a nonexistent document.
- Uploads must be rate-limited per authenticated user.
- Filenames must be validated before persistence. Invalid filenames are rejected rather than silently rewritten.
- A filename is invalid if it:
  - is missing, empty, or whitespace-only
  - exceeds 255 characters
  - contains `/` or `\` path separators
  - contains NUL or other control characters
  - is exactly `.` or `..`
- Invalid filenames are not truncated or automatically renamed.
- User-provided filenames must never be used as object-storage keys.
- File-size limits and supported file-type rules must be enforced by the server.

### File Size

- Empty files are not allowed.
- Maximum file size: 100 MiB.
- Maximum size in bytes: `104857600`.
- The file-size rule applies to the actual uploaded file part, not the entire multipart HTTP request.
- The server derives file size from the actual parsed file part. Client-provided size information and the overall HTTP `Content-Length` are not authoritative document sizes.
- The HTTP/multipart layer must enforce a separate coarse request-size limit above 100 MiB so multipart overhead does not cause a valid near-limit file to be rejected.

### Performance and Availability

- Milestone 1 has no formal uptime SLA.
- Milestone 1 has no strict latency target beyond reasonable interactive API behavior.
- Initial traffic is expected to be very small.
- The design should avoid choices that unnecessarily prevent future horizontal scaling.

## Constraints

- The system is API-only. No UI is included in Milestone 1.
- The application is a single Spring Boot service.
- Microservices are out of scope.
- Document metadata is stored in PostgreSQL.
- Document bytes are stored in private object storage.
- Uploads and downloads pass through the Spring Boot API in Milestone 1.
- Clients do not communicate directly with object storage.
- Uploads are synchronous from the user's perspective.
- Downloads are synchronous from the user's perspective.
- Only one file may be uploaded per request.
- Bulk upload is out of scope.
- Sharing documents between users is out of scope.
- Folder organization is out of scope.
- Soft delete and document recovery are out of scope.
- Malware and antivirus scanning are out of scope for Milestone 1.
- Production-grade registration, password reset, MFA, and similar identity-management features are out of scope.
- A simplified authentication mechanism will provide authenticated identities during development and testing.
- Pagination for document listing is out of scope for Milestone 1.

## Assumptions

- Milestone 1 initially supports a single authentication issuer.
- The authenticated identity provides a stable subject identifier.
- Each document has exactly one owner.
- Document contents are immutable for the lifetime of the project.
- PostgreSQL and object storage are independent durable systems and may fail independently.
- Cross-system operations involving PostgreSQL and object storage are not part of a single atomic transaction.
- The application is responsible for compensating or reconciling partial failures between metadata persistence and object storage.
- Storage cost and capacity are not significant constraints at Milestone 1 scale.
- The number of documents per user is initially small enough that returning the full collection without pagination is acceptable.

## Out of Scope for Milestone 1

- UI
- folders
- sharing
- document editing
- document versioning
- soft delete / recycle bin
- malware scanning
- bulk upload
- direct-to-object-storage signed uploads
- pagination
- multi-issuer authentication
- production-grade identity management
- microservices
