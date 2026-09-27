# Secure Document Platform — Milestone 1 Component Contracts

## Purpose

This document defines the responsibilities and interaction contracts between the main Milestone 1 application components.

It intentionally does **not** repeat:

- HTTP endpoint definitions or response semantics — see `api.md`
- database columns, constraints, indexes, MIME allowlist, or file-size limits — see `data-model.md`
- the UUIDv7 decision — see `decisions/0001-use-uuidv7-for-document-ids.md`
- the object-storage decision — see `decisions/0002-store-files-in-object-storage.md`
- the decision to proxy uploads/downloads through the API — see `decisions/0003-proxy-file-transfers-through-api.md`

The focus here is strictly on **component responsibilities and contracts**.

---

## Request / Response Handler

### Responsibilities

- Accept and parse HTTP requests.
- Obtain the authenticated subject from the authentication boundary.
- Translate request data into application-level inputs.
- Invoke `DocumentService`.
- Translate service results and failures into HTTP responses.

### Does Not Own

- document authorization
- document ID generation
- filename collision policy
- persistence
- object storage
- MIME detection
- cross-system failure coordination

---

## Authentication Boundary

### Responsibilities

- Determine whether a request is authenticated.
- Provide the stable authenticated subject identifier.

### Contract

```text
getAuthenticatedSubject() -> ownerSubject
```

### Does Not Own

- document-level authorization
- repository access
- document validation
- object-storage access

---

## DocumentService

### Responsibilities

`DocumentService` coordinates document workflows and owns document-level authorization.

It is responsible for:

- document creation
- duplicate filename conflict handling
- metadata retrieval
- document listing
- content retrieval
- permanent deletion
- authorization of document-specific operations
- coordination between persistence and object storage
- handling partial failures between those systems

### Contract

```text
upload(ownerSubject, fileInput)

getDocument(documentId, ownerSubject)

listDocuments(ownerSubject)

openDocumentContent(documentId, ownerSubject)

deleteDocument(documentId, ownerSubject)
```

### Authorization

For document-specific operations, `DocumentService` uses ownership-scoped repository operations rather than retrieving by document ID alone.

### Duplicate Filename Conflict

`DocumentService` checks whether the candidate filename is already in use by the same owner.

If it is already in use, the upload is rejected rather than automatically renamed.

The database constraint remains the final integrity backstop. If concurrent uploads race and the constraint rejects one insert, the failure is treated as the same filename-conflict outcome rather than retried with a different name.

### Deletion Coordination

Deletion is coordinated in this order:

```text
1. Load the ownership-scoped document.
2. Delete the object-store content.
3. Delete the ownership-scoped metadata row.
```

If the object content has already been deleted, object deletion is treated as a successful end state so a retry can continue to metadata deletion.

If object deletion succeeds but metadata deletion fails, the operation fails and may be retried.

---

## DocumentValidator

### Responsibilities

- validate filename rules
- validate filenames and reject invalid input
- validate file size

### Contract

```text
validateFileName(fileName)

validateFileSize(fileSizeBytes)
```

Validation rules and limits are defined in `data-model.md` rather than duplicated here.

`fileSizeBytes` is the actual parsed file-part size supplied to the application. `DocumentValidator` does not treat the overall HTTP `Content-Length` as the document size.

Milestone 1 rejects invalid filenames rather than truncating, renaming, or otherwise silently rewriting them.

### Does Not Own

- MIME detection
- persistence
- authorization
- object storage
- duplicate filename lookup
- HTTP/multipart request-size enforcement

---

## MimeTypeResolver

### Responsibilities

- inspect document content
- determine the canonical MIME type
- report when content cannot be identified as a supported type

### Contract

```text
resolve(contentStream) -> canonicalMimeType
```

The server-detected canonical MIME type is authoritative. A mismatch with the client-provided MIME type or filename extension is not itself an error when the detected type is supported. The validated filename remains unchanged.

Unsupported or unidentifiable content is rejected by the upload workflow as `UNSUPPORTED_FILE_TYPE`.

Allowed MIME types are defined in `data-model.md`.

---

## IdGenerator

### Responsibilities

- generate new document identifiers according to the UUID strategy selected for the project

### Contract

```text
generate() -> documentId
```

Existing documents loaded from persistence retain their stored ID; reconstruction does not generate a new one.

The UUID strategy itself is documented in ADR 0001.

---

## DocumentRepository

### Purpose

Expose document metadata persistence operations to `DocumentService`.

Document-specific read and delete operations are ownership-scoped.

### Contract

```text
save(document)

findByDocumentIdAndOwnerSubject(documentId, ownerSubject)

findAllByOwnerSubject(ownerSubject)

existsByOwnerSubjectAndFileName(ownerSubject, fileName)

deleteByDocumentIdAndOwnerSubject(documentId, ownerSubject)
```

### Responsibilities

- persist document metadata
- retrieve an owned document
- retrieve documents belonging to an owner
- support duplicate-filename checks
- delete owned document metadata

### Does Not Own

- filename policy
- HTTP semantics
- MIME detection
- file content
- object storage

---

## ObjectStorage

### Purpose

Store and retrieve opaque document content.

Authorization occurs before `DocumentService` accesses this component; object storage itself does not determine document ownership.

### Contract

```text
store(documentId, contentStream, contentLength)

open(documentId) -> contentStream

delete(documentId)
```

`open(documentId)` must distinguish an absent object from an object-storage dependency/read failure so `DocumentService` can handle those outcomes differently.

### Streaming

The boundary is stream-oriented rather than byte-array-oriented so file content does not need to be fully materialized in application memory.

### Delete Semantics

```text
delete(documentId)
```

is idempotent from the application's perspective: an already-absent object is an acceptable successful end state.

### Does Not Own

- authentication
- authorization
- metadata
- filename policy
- MIME policy

---

## Document Model

The document model represents document metadata.

Its exact persisted fields and constraints are defined in `data-model.md`.

The model does not:

- generate its own ID
- inspect file content
- detect MIME type
- persist itself
- access object storage
- authenticate or authorize users

---

## Component Relationship

```text
HTTP Request
    |
    v
Request / Response Handler
    |
    +---- Authentication Boundary
    |
    v
DocumentService
    |
    +---- DocumentValidator
    +---- MimeTypeResolver
    +---- IdGenerator
    +---- DocumentRepository
    +---- ObjectStorage
```

`DocumentService` is the workflow coordinator. The other components expose focused capabilities and should not independently coordinate the overall document lifecycle.

---

## Deferred Contract Details

The following remain intentionally undecided:

- exact Spring controller types
- exact Java stream/resource types
- stream lifecycle and close ownership
- exact MIME-detection implementation
- exact repository implementation technology
- exact object-storage client
