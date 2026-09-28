# Secure Document Platform — Milestone 1 Test Cases

## Purpose

This document records the Milestone 1 upload test cases and acceptance criteria agreed on so far.

It distinguishes externally observable acceptance criteria from lower-level unit-test interaction checks where useful.

---

## Upload — Happy Path

### Valid Upload

**Given**
- an authenticated user
- a valid supported document

**When**
- the client sends `POST /documents`

**Then**
- return `201 Created`
- include a `Location` header identifying the created document
- persist document metadata in PostgreSQL with the authenticated owner subject and derived metadata
- store the document bytes in object storage

---

## Upload — Authentication and Ownership

### Authenticated Request

**Given**
- an authenticated user
- a valid document

**When**
- the client sends `POST /documents`

**Then**
- the request is allowed to continue through the upload workflow
- a successful upload returns `201 Created`

### Unauthenticated Request

**Given**
- an unauthenticated request
- an otherwise valid document

**When**
- the client sends `POST /documents`

**Then**
- return `401 Unauthorized`
- do not persist metadata
- do not write document content to object storage

### Owner Is Derived From Authentication

**Given**
- an authenticated user
- a valid document

**When**
- the client uploads the document

**Then**
- the stored owner subject comes from the authenticated identity
- the client cannot choose or override the owner subject

---

## Upload — File Size Validation

The 100 MiB limit applies to the actual uploaded file part, not the entire multipart request. The server-derived file-part size is authoritative for validation and for persisted `file_size_bytes`.

### Empty File

**Given**
- an authenticated user
- a zero-byte file

**When**
- the client sends `POST /documents`

**Then**
- return `400 Bad Request`
- return application error code `EMPTY_FILE`
- do not perform MIME detection
- do not query PostgreSQL for a filename conflict
- do not persist metadata
- do not write document content to object storage

### Oversized File

**Given**
- an authenticated user
- a file whose actual file-part size is larger than 100 MiB

**When**
- the client sends `POST /documents`

**Then**
- return `413 Payload Too Large`
- return application error code `FILE_TOO_LARGE`
- do not perform MIME detection
- do not query PostgreSQL for a filename conflict
- do not persist metadata
- do not write document content to object storage

### Multipart Overhead Does Not Count Toward the File Limit

**Given**
- an authenticated user
- a valid file whose actual file-part size is at or below 100 MiB
- a multipart request whose total HTTP size is larger than the file size because of multipart framing overhead

**When**
- the client sends `POST /documents`

**Then**
- do not reject the document merely because the overall multipart request is larger than 100 MiB
- apply the 100 MiB business rule to the actual file part
- continue normal validation if the file-part size is valid

### Size Source Is Server-Derived

**Given**
- an authenticated user
- an uploaded file

**When**
- the server validates and persists its size

**Then**
- use the actual parsed file-part size
- do not use the overall HTTP `Content-Length` as `file_size_bytes`
- do not treat client-provided size information as authoritative

### Unit-Test Interaction

At the service/unit-test level, verify that the document-size validation path is invoked before filename validation, MIME detection, duplicate-filename lookup, persistence, or object storage.

---

## Upload — Validation Precedence

Upload validation stops at the first failure using this order:

```text
file size
    ↓
filename
    ↓
detected MIME type
    ↓
duplicate filename
```

The following combined-invalid cases should prove the precedence:

- oversized file + invalid filename → `413 FILE_TOO_LARGE`
- invalid filename + unsupported MIME type → `400 INVALID_FILE_NAME`
- unsupported MIME type + duplicate filename → `415 UNSUPPORTED_FILE_TYPE`

An oversized file is rejected before MIME inspection.

---

## Upload — Filename Validation

Milestone 1 rejects invalid filenames rather than silently sanitizing, truncating, or renaming them.

A filename is invalid if it:

- is missing
- is empty
- contains only whitespace
- exceeds 255 characters
- contains `/`
- contains `\`
- contains NUL or other control characters
- is exactly `.`
- is exactly `..`

### Invalid Filename

**Given**
- an authenticated user
- an invalid filename

**When**
- the client sends `POST /documents`

**Then**
- return `400 Bad Request`
- return application error code `INVALID_FILE_NAME`
- do not persist metadata
- do not write document content to object storage

### Concrete Filename Cases

The invalid-filename behavior should be tested with at least:

- empty filename
- whitespace-only filename
- filename longer than 255 characters
- filename containing `/`
- filename containing `\`
- filename containing a control character
- `.`
- `..`

### Unit-Test Interaction

At the service/unit-test level, verify that `DocumentValidator` is invoked.

---

## Upload — MIME Type Validation

### Unsupported File Type

**Given**
- an authenticated user
- a file whose detected content type is unsupported

**When**
- the client sends `POST /documents`

**Then**
- return `415 Unsupported Media Type`
- return application error code `UNSUPPORTED_FILE_TYPE`
- do not check for a duplicate filename
- do not persist metadata
- do not write document content to object storage

### Supported Detected Type With Client/Extension Mismatch

**Given**
- an authenticated user
- filename `report.pdf`
- client-provided MIME type `application/pdf`
- actual content detected as `image/png`
- `image/png` is supported

**When**
- the client sends `POST /documents`

**Then**
- accept the upload
- a successful upload returns `201 Created`
- preserve the validated filename as `report.pdf`
- persist canonical content type `image/png`
- do not treat the client-provided MIME type or filename extension as authoritative

### Unidentifiable File Type

**Given**
- an authenticated user
- valid file size
- valid filename
- MIME detection cannot identify the content as a supported type

**When**
- the client sends `POST /documents`

**Then**
- return `415 Unsupported Media Type`
- return application error code `UNSUPPORTED_FILE_TYPE`
- do not check for a duplicate filename
- do not persist metadata
- do not write document content to object storage

### Unit-Test Interaction

At the service/unit-test level, verify that MIME detection is invoked.

The server-detected canonical MIME type is authoritative. Unsupported and unidentifiable content share the `UNSUPPORTED_FILE_TYPE` failure outcome.

---

## Upload — Duplicate Filename

Filename uniqueness is case-sensitive within a single owner's document space.

### Same Owner, Exact Same Filename

**Given**
- an authenticated user
- the user already owns `report.pdf`

**When**
- the user uploads another document named `report.pdf`

**Then**
- check whether the filename already exists for that owner
- return `409 Conflict`
- return application error code `DOCUMENT_NAME_CONFLICT`
- do not persist new metadata
- do not write document content to object storage

### Different Owner, Same Filename

**Given**
- user A owns `report.pdf`
- authenticated user B does not own a document named `report.pdf`

**When**
- user B uploads `report.pdf`

**Then**
- allow the upload
- a successful upload returns `201 Created`

### Same Owner, Different Case

**Given**
- an authenticated user
- the user already owns `report.pdf`

**When**
- the user uploads `Report.pdf`

**Then**
- treat the filenames as distinct
- allow the upload
- a successful upload returns `201 Created`

---

## Upload — Concurrent Duplicate Filename Race

The application-level duplicate check is not sufficient by itself because two requests may both observe that the filename is available before either insert commits.

The database `UNIQUE(owner_subject, file_name)` constraint is the final integrity backstop.

### Losing Request in a Concurrent Race

**Given**
- no matching filename exists initially for the owner
- two uploads for the same owner and same case-sensitive filename run concurrently
- both requests pass the application-level existence check

**When**
- one metadata insert succeeds
- the other metadata insert loses the race and violates the database unique constraint

**Then**
- the winning request continues normally
- the losing request returns `409 Conflict`
- the losing request returns application error code `DOCUMENT_NAME_CONFLICT`
- the losing request does not write anything to object storage
- the application does not retry with an automatically generated alternate filename

---

## Upload — PostgreSQL Failure

### Metadata Persistence Fails

**Given**
- an authenticated user
- a valid document

**When**
- PostgreSQL metadata persistence fails

**Then**
- return `500 Internal Server Error`
- do not write anything to object storage

### Unit-Test Interaction

At the service/unit-test level, verify that no object-storage interaction occurs after the metadata save fails.

---

## Upload — Object-Storage Failure

### Object Write Fails After Metadata Save

**Given**
- an authenticated user
- a valid document
- metadata persistence succeeds

**When**
- the object-storage write fails

**Then**
- attempt to delete the metadata using the document ID and owner subject
- return `503 Service Unavailable`

### Unit-Test Verification

At the service/unit-test level:

- verify the compensating ownership-scoped metadata delete is attempted
- verify the service reports the expected failure outcome

### Integration-Test Verification

With a real PostgreSQL instance:

- verify the metadata row no longer exists after successful compensation

No extra production read is required solely to verify that the compensating delete worked.

---

## Upload — Compensation Failure

### Object Write Fails and Metadata Cleanup Also Fails

**Given**
- an authenticated user
- a valid document
- metadata persistence succeeds

**When**
- the object-storage write fails
- the compensating PostgreSQL metadata delete also fails

**Then**
- return `503 Service Unavailable`
- the metadata row remains in PostgreSQL
- no successfully stored object exists
- record/log the compensation failure so the inconsistency can be identified later

### Known Milestone 1 Consequence

Because the stale metadata row remains and filename uniqueness is enforced by:

```text
UNIQUE(owner_subject, file_name)
```

a retry using the same filename may receive:

```text
409 DOCUMENT_NAME_CONFLICT
```

until the stale metadata is cleaned up.

Milestone 1 accepts this as a known limitation rather than introducing document lifecycle states or a reconciliation subsystem.

---

## Unit Tests vs Integration Tests

Use the two levels for different purposes.

### Unit Tests

Unit tests primarily prove orchestration and interaction behavior, for example:

- a validator was called
- object storage was not called after a database failure
- a compensating repository delete was attempted
- a dependency failure maps to the expected service outcome

### Integration Tests

Integration tests primarily prove actual persisted state and infrastructure behavior, for example:

- a metadata row exists after a successful upload
- a metadata row is gone after successful compensation
- the unique database constraint rejects a concurrent duplicate
- document bytes actually exist or do not exist in object storage as expected

---

## Upload Test Areas Not Yet Fully Defined

The following areas have not yet been fully worked through as acceptance tests:

- exact response-body schema for application errors
- logging and metrics assertions for failure paths
- any future reconciliation behavior for stale metadata

# Read and Delete Endpoint Test Cases

## List Documents — `GET /documents`

### Authenticated User With Documents

**Given**
- an authenticated user
- the user owns one or more documents

**When**
- the client sends `GET /documents`

**Then**
- return `200 OK`
- return only documents owned by the authenticated user
- do not include documents owned by other users

### Authenticated User With No Documents

**Given**
- an authenticated user
- the user owns no documents

**When**
- the client sends `GET /documents`

**Then**
- return `200 OK`
- return an empty list: `[]`

### Unauthenticated Request

**Given**
- an unauthenticated request

**When**
- the client sends `GET /documents`

**Then**
- return `401 Unauthorized`
- do not query PostgreSQL for owner-scoped documents

### PostgreSQL Lookup Failure

**Given**
- an authenticated user

**When**
- the client sends `GET /documents`
- the PostgreSQL document-list lookup fails

**Then**
- return `500 Internal Server Error`
- do not access object storage

---

## Get Document Metadata — `GET /documents/{documentId}`

### Owned Document Exists

**Given**
- an authenticated user
- the requested document exists
- the document belongs to the authenticated user

**When**
- the client sends `GET /documents/{documentId}`

**Then**
- return `200 OK`
- return the document metadata
- include:
  - `documentId`
  - `fileName`
  - `fileSizeBytes`
  - `contentType`
  - `uploadedAt`
- do not expose `ownerSubject`

### Document Does Not Exist

**Given**
- an authenticated user
- the requested document does not exist

**When**
- the client sends `GET /documents/{documentId}`

**Then**
- return `404 Not Found`
- do not access object storage

### Document Belongs to Another User

**Given**
- an authenticated user
- the requested document exists
- the document belongs to another user

**When**
- the client sends `GET /documents/{documentId}`

**Then**
- return `404 Not Found`
- do not access object storage

The same external response is intentionally used for nonexistent and unauthorized documents so the API does not reveal whether another user's document exists.

### Unauthenticated Request

**Given**
- an unauthenticated request

**When**
- the client sends `GET /documents/{documentId}`

**Then**
- return `401 Unauthorized`
- do not query PostgreSQL
- do not access object storage

### PostgreSQL Lookup Failure

**Given**
- an authenticated user

**When**
- the client sends `GET /documents/{documentId}`
- the ownership-scoped PostgreSQL lookup fails

**Then**
- return `500 Internal Server Error`
- do not access object storage

---

## Download Document Content — `GET /documents/{documentId}/content`

### Successful Download

**Given**
- an authenticated user
- owned document metadata exists in PostgreSQL
- the corresponding object exists in object storage

**When**
- the client sends `GET /documents/{documentId}/content`

**Then**
- return `200 OK`
- stream the document bytes
- set `Content-Type` to the stored canonical MIME type
- set `Content-Disposition` using the validated filename
- set `Content-Length` from stored `file_size_bytes` if included in the response

Authorization is established through the ownership-scoped PostgreSQL metadata lookup before object storage is accessed.

### Document Does Not Exist

**Given**
- an authenticated user
- the requested document does not exist

**When**
- the client sends `GET /documents/{documentId}/content`

**Then**
- return `404 Not Found`
- do not access object storage

### Document Belongs to Another User

**Given**
- an authenticated user
- the requested document exists
- the document belongs to another user

**When**
- the client sends `GET /documents/{documentId}/content`

**Then**
- return `404 Not Found`
- do not access object storage

### PostgreSQL Lookup Failure

**Given**
- an authenticated user

**When**
- the client sends `GET /documents/{documentId}/content`
- the ownership-scoped PostgreSQL lookup fails

**Then**
- return `500 Internal Server Error`
- do not access object storage

### Metadata Exists but Object Is Missing

**Given**
- an authenticated user
- owned metadata exists in PostgreSQL
- the corresponding object is missing from object storage

**When**
- the client sends `GET /documents/{documentId}/content`

**Then**
- return `500 Internal Server Error`
- log or record the cross-system consistency failure

A missing object is treated as an internal consistency failure, not as normal `404` behavior and not as temporary dependency unavailability.

### Object Storage Unavailable Before Streaming Begins

**Given**
- an authenticated user
- owned metadata exists in PostgreSQL
- the corresponding object exists

**When**
- the client sends `GET /documents/{documentId}/content`
- object storage is unavailable, times out, or otherwise fails before the successful response is committed

**Then**
- return `503 Service Unavailable`

### Object-Storage Read Fails After Streaming Begins

**Given**
- an authenticated user
- owned document metadata exists
- object storage successfully begins streaming the document
- the `200 OK` response has already been committed

**When**
- the object-storage read fails before all document bytes have been sent

**Then**
- abort or terminate the response stream
- do not attempt to replace the committed response with another HTTP status
- log or record the streaming failure
- the client receives an incomplete response
- the client may retry the full GET

If `Content-Length` is present, the client may detect that fewer bytes were received than expected. The HTTP client or transport may also expose an I/O or premature-termination error.

### Unauthenticated Request

**Given**
- an unauthenticated request

**When**
- the client sends `GET /documents/{documentId}/content`

**Then**
- return `401 Unauthorized`
- do not query PostgreSQL
- do not access object storage

---

## Delete Document — `DELETE /documents/{documentId}`

### Successful Delete

**Given**
- an authenticated user
- the requested document exists
- the document belongs to the authenticated user
- the corresponding object exists in object storage

**When**
- the client sends `DELETE /documents/{documentId}`

**Then**
- delete the object from object storage first
- delete the ownership-scoped metadata row from PostgreSQL second
- return `204 No Content`

### Object Already Missing

**Given**
- an authenticated user
- owned metadata exists in PostgreSQL
- the corresponding object is already absent from object storage

**When**
- the client sends `DELETE /documents/{documentId}`

**Then**
- treat the already-absent object as a successful object-deletion state
- delete the ownership-scoped metadata row from PostgreSQL
- return `204 No Content`

This makes retries safe when a prior delete removed the object but did not successfully remove the metadata.

### Unauthenticated Request

**Given**
- an unauthenticated request

**When**
- the client sends `DELETE /documents/{documentId}`

**Then**
- return `401 Unauthorized`
- do not query PostgreSQL
- do not access object storage

### Document Does Not Exist

**Given**
- an authenticated user
- the requested document does not exist

**When**
- the client sends `DELETE /documents/{documentId}`

**Then**
- perform the ownership-scoped PostgreSQL lookup
- return `404 Not Found`
- do not access object storage
- do not delete any metadata

### Document Belongs to Another User

**Given**
- an authenticated user
- the requested document exists
- the document belongs to another user

**When**
- the client sends `DELETE /documents/{documentId}`

**Then**
- perform the ownership-scoped PostgreSQL lookup
- return `404 Not Found`
- do not access object storage
- do not delete any metadata

### PostgreSQL Ownership Lookup Failure

**Given**
- an authenticated user

**When**
- the client sends `DELETE /documents/{documentId}`
- the ownership-scoped PostgreSQL lookup fails

**Then**
- return `500 Internal Server Error`
- do not access object storage

### Object-Storage Delete Failure

**Given**
- an authenticated user
- the ownership-scoped PostgreSQL lookup succeeds
- the user owns the document

**When**
- object-storage deletion fails

**Then**
- return `503 Service Unavailable`
- do not delete the PostgreSQL metadata row

The metadata remains intact so the delete may be retried later.

### Metadata Delete Fails After Object Delete

**Given**
- an authenticated user
- the user owns the document
- object deletion succeeds

**When**
- the ownership-scoped PostgreSQL metadata deletion fails

**Then**
- return `500 Internal Server Error`
- the object remains absent
- the stale metadata row remains

A retry is safe because object deletion is idempotent from the application's perspective: an already-absent object is treated as a successful deletion state, allowing the workflow to retry metadata deletion.

---

## Read/Delete Test Areas Not Yet Fully Defined

The following areas remain intentionally deferred:

- exact response-body schema for generic `500` and `503` errors
- logging and metrics assertions beyond the explicitly identified consistency and streaming failures
- any future reconciliation behavior for stale metadata
