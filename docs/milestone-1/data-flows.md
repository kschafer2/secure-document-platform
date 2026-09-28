# Secure Document Platform — Milestone 1 Data Flows

## Purpose

This document records the end-to-end application workflows for Milestone 1.

It focuses on:

- component interaction order
- durable-write ordering
- authorization points
- failure handling
- compensation behavior
- accepted consistency tradeoffs

---

## Data Flow Index

| Flow |
|---|
| Upload document |
| List documents |
| Get document metadata |
| Download document content |
| Delete document |

---

## Common Authentication Failure

All Milestone 1 endpoints require authentication.

```text
unauthenticated request
    ↓
return 401 Unauthorized
    ↓
do not invoke PostgreSQL or object storage
```

---

# 1. Upload Document

## Goal

Accept a document from an authenticated user, validate it, persist its metadata in PostgreSQL, store its content in object storage, and return success only after both durable systems have successfully completed their required work.

## Participating Components

```text
Client
Request / Response Handler
Authentication Boundary
DocumentService
DocumentValidator
MimeTypeResolver
IdGenerator
DocumentRepository
ObjectStorage
```

## Happy-Path Sequence

```text
1. Client sends an upload request.

2. Authentication Boundary authenticates the request and provides
   the stable ownerSubject.

3. Before DocumentService processes the upload, the HTTP/multipart
   infrastructure parses the request and exposes the uploaded file part.
   A separate coarse request-size limit protects the server while allowing
   for multipart overhead above the 100 MiB document limit.

4. Request / Response Handler passes the authenticated ownerSubject
   and uploaded file input to DocumentService.

5. DocumentService asks DocumentValidator to validate the actual parsed
   file-part size. Empty files are rejected with EMPTY_FILE and files over
   100 MiB are rejected with FILE_TOO_LARGE.

6. DocumentService asks DocumentValidator to validate the filename.
   Invalid filenames are rejected rather than rewritten.

7. DocumentService asks MimeTypeResolver to inspect the file contents
   and determine the canonical MIME type.

8. DocumentService asks DocumentRepository whether the candidate
   filename already exists for the owner.

9. If the filename already exists:
   → return 409 Conflict.
   → do not persist metadata or write to object storage.

10. If the filename is available, DocumentService asks IdGenerator for
   a new document ID.

11. DocumentService constructs the document metadata.

12. DocumentService saves the metadata through DocumentRepository.

13. The metadata database operation completes before the object-store
    upload begins.

14. DocumentService streams the document content to ObjectStorage.

15. ObjectStorage completes the write successfully.

16. DocumentService reports successful creation to the
    Request / Response Handler.

17. Request / Response Handler returns the successful upload response.
```

## Why Validation Happens in This Order

Validation is ordered from relatively cheap checks toward more expensive work:

```text
actual file-part size
    ↓
filename validation
    ↓
content inspection / MIME detection
    ↓
duplicate-filename lookup
    ↓
persistence and object storage
```

The goal is to reject invalid uploads before performing unnecessary expensive work.

The duplicate-filename check occurs only after the document itself has passed validation.

The server-detected canonical MIME type is authoritative. If the detected type is supported, disagreement with the client-provided MIME type or filename extension does not cause rejection; the validated filename is preserved and the detected MIME type is stored. Unsupported or unidentifiable content is rejected as `415 UNSUPPORTED_FILE_TYPE`.

The document ID is generated only after the service knows the upload is acceptable and the filename is available.

---

## Durable-Write Ordering

Milestone 1 writes to PostgreSQL before writing document bytes to object storage:

```text
PostgreSQL metadata
        ↓
Object storage content
```

The overall upload orchestration is intentionally **not** one long database transaction.

Conceptually:

```text
validate
   ↓
INSERT metadata
   ↓
database operation completes
   ↓
stream file to object storage
```

A database transaction is not held open while a potentially large file is transferred over the network to object storage.

PostgreSQL and object storage remain independent durable systems and do not participate in one shared transaction.

---

## Failure Cases

### Validation Failure

If any validation step fails:

```text
validation fails
    ↓
do not persist metadata
    ↓
do not write to object storage
    ↓
return upload failure
```

File-size validation uses the actual parsed file-part size:

```text
0 bytes
    → 400 EMPTY_FILE

1 through 104857600 bytes
    → continue validation

more than 104857600 bytes
    → 413 FILE_TOO_LARGE
```

Because file-size validation occurs first, MIME inspection, duplicate-filename lookup, persistence, and object storage are not reached when the file size is invalid.

No compensation is necessary because neither durable system has been modified.

---

### Metadata Persistence Failure

If the PostgreSQL metadata save fails:

```text
metadata save fails
    ↓
do not call object storage
    ↓
return upload failure
```

No object has been created, so there is nothing to compensate for.

---

### Object-Storage Write Failure

If the metadata save succeeds but the object-store write fails:

```text
metadata save succeeds
    ↓
object-store write fails
    ↓
attempt compensating metadata deletion
    ↓
return upload failure
```

The compensating operation is ownership-scoped:

```text
deleteByDocumentIdAndOwnerSubject(documentId, ownerSubject)
```

The upload is still considered failed even if compensation succeeds.

---

### Compensation Failure

If both the object-store write and compensating metadata deletion fail:

```text
metadata exists
    ↓
object content does not
    ↓
compensating metadata delete fails
    ↓
stale metadata remains
```

Milestone 1 accepts this state temporarily.

A future reconciliation process may identify and remove stale metadata.

The failure is returned to the caller.

---

## Accepted Temporary Inconsistency

There is a window between metadata persistence and completion of the object-store write where:

```text
metadata exists
object content is not yet available
```

Milestone 1 accepts this temporary visibility.

The reasoning is:

- documents are private to one owner
- document sharing is out of scope
- per-owner concurrent request volume is expected to be low
- adding an `UPLOADING` document state would introduce additional complexity that is not currently justified

If later requirements introduce document sharing, background processing, substantially higher concurrency, or other workflows that make this state problematic, the design should be reconsidered.

---

## Duplicate Filename Conflicts

`DocumentService` checks whether the owner already has the requested filename before insertion.

If the filename already exists, the upload is rejected with `409 Conflict`. The service does not automatically rename the document.

The database unique constraint remains the final integrity guard.

If two concurrent uploads for the same owner race and both pass the application-level check:

```text
both service checks succeed
    ↓
one database insert succeeds
    ↓
the other violates the unique constraint
```

The constraint violation is treated as the same filename-conflict outcome and returned as `409 Conflict`.

---

## Transaction Boundary

The upload orchestration itself is not wrapped in one `@Transactional` boundary.

Desired behavior:

```text
short database operation
        ↓
commit / complete
        ↓
potentially slow object-store network operation
```

This avoids holding a database transaction open while transferring a document that may be large.

If compensation is required, the compensating database deletion occurs as a separate database operation.

---

## Upload Flow Summary

```text
Client
  |
  v
Authenticate
  |
  v
Validate actual file-part size
  |
  v
Validate filename
  |
  v
Inspect content / resolve MIME type
  |
  v
Check filename conflict
  |
  v
Generate document ID
  |
  v
Persist metadata
  |
  v
Stream content to object storage
  |
  +------------------------------+
  | success                      | failure
  v                              v
Return success          Compensating metadata delete
                                  |
                                  v
                           Return failure
```

---

# 2. List Documents

## Sequence

```text
1. Authenticate the request and obtain ownerSubject.

2. Retrieve metadata for all documents owned by ownerSubject.

3. Return 200 OK with the metadata list.

4. If no documents exist for the owner:
   → return 200 OK with an empty list.
```

No object-storage access is required for this flow.

If the PostgreSQL listing lookup fails:

```text
PostgreSQL lookup fails
    ↓
return 500 Internal Server Error
```

---

# 3. Get Document Metadata

## Sequence

```text
1. Authenticate the request and obtain ownerSubject.

2. Retrieve document metadata using documentId + ownerSubject.

3. If the metadata is found:
   → return 200 OK with the document metadata.

4. If the metadata is not found:
   → return 404 Not Found.
```

The ownership-scoped lookup intentionally returns the same not-found result for both:

- a nonexistent document
- a document owned by another user

No object-storage access is required for this flow.

If the ownership-scoped PostgreSQL lookup fails:

```text
PostgreSQL lookup fails
    ↓
return 500 Internal Server Error
```

---

# 4. Download Document Content

## Goal

Return the content of a document owned by the authenticated user without loading the entire file into application memory.

The download flow verifies ownership through metadata before object-storage access and streams the file content to the client.

## Participating Components

```text
Client
Request / Response Handler
Authentication Boundary
DocumentService
DocumentRepository
ObjectStorage
```

## Happy-Path Sequence

```text
1. Client sends a request for document content.

2. Authentication Boundary authenticates the request and provides
   the stable ownerSubject.

3. Request / Response Handler passes documentId and ownerSubject
   to DocumentService.

4. DocumentService queries DocumentRepository using both:
   - documentId
   - ownerSubject

5. If the ownership-scoped metadata lookup succeeds,
   DocumentService asks ObjectStorage to open the content stream
   for the document ID.

6. ObjectStorage successfully opens the content stream.

7. DocumentService returns the content stream together with the
   metadata required to build the response, including:
   - filename
   - canonical content type
   - file size

8. Request / Response Handler creates the successful HTTP response.

9. The application streams the document bytes to the client.
```

## PostgreSQL Lookup Failure

If the ownership-scoped metadata lookup itself fails:

```text
PostgreSQL lookup fails
    ↓
do not access object storage
    ↓
return 500 Internal Server Error
```

---

## Ownership Failure

If the ownership-scoped metadata lookup returns no document:

```text
metadata not found
    ↓
do not access object storage
    ↓
return 404
```

This intentionally covers both:

- a document ID that does not exist
- a document that exists but belongs to another user

Object storage is accessed only after ownership has been established.

---

## Object Missing After Metadata Lookup

If PostgreSQL contains valid owned metadata but object storage reports that the corresponding object does not exist:

```text
metadata exists
    ↓
object missing
    ↓
internal consistency invariant is broken
```

This is treated as an internal server failure rather than as a normal not-found result.

The application should:

```text
record/log the consistency failure
    ↓
return 500 Internal Server Error
```

A `404` would incorrectly imply that the document itself does not exist even though the application's metadata store says it does.

---

## Object Storage Unavailable Before Streaming

If object storage is unavailable, times out, or otherwise fails before the response stream has begun:

```text
metadata lookup succeeds
    ↓
object-storage dependency fails
    ↓
return 503 Service Unavailable
```

Because the HTTP response has not yet been committed, a normal HTTP error response can still be returned.

---

## Failure After Streaming Begins

Once a successful response has been committed and document bytes are being sent to the client, the application cannot replace that response with a different HTTP status.

For example:

```text
200 response begins
    ↓
20 MiB of a 100 MiB document is streamed
    ↓
object-store read fails
    ↓
response stream terminates early
```

At this point the application should:

- abort/close the response stream
- log the failure with useful request/document context
- emit an appropriate failure metric

The client receives an incomplete download and may retry the request.

If `Content-Length` is present, a client can detect that fewer bytes were received than expected. The HTTP client or transport may also surface an I/O or premature-termination error.

---

## Retry Behavior

Document retrieval is read-only, so retrying the entire GET is safe.

Milestone 1 does **not** attempt server-side retry after streaming has already begun.

Doing so would require more complex behavior such as:

- resuming from a byte offset
- HTTP range requests
- coordinating partial client delivery

Those capabilities are intentionally deferred.

For Milestone 1:

```text
download stream fails
    ↓
client receives incomplete download
    ↓
client retries the full GET if desired
```

---

## Download Flow Summary

```text
Client
  |
  v
Authenticate
  |
  v
Find metadata by documentId + ownerSubject
  |
  +------------------------------+
  | found                        | not found
  v                              v
Open object stream              404
  |
  +------------------------------+
  | opened                       | object missing
  |                              v
  |                             500
  |
  +------------------------------+
  | dependency available         | unavailable before streaming
  v                              v
Begin 200 response              503
  |
  v
Stream bytes
  |
  +------------------------------+
  | completes                    | fails midstream
  v                              v
Download complete        abort stream + log/metric
                                 |
                                 v
                          client may retry GET
```

## Milestone 1 Limitations

Milestone 1 does not include:

- byte-range requests
- resumable downloads
- automatic midstream retries
- partial-download recovery

---

# 5. Delete Document

## Sequence

```text
1. Authenticate the request and obtain ownerSubject.

2. Retrieve document metadata using documentId + ownerSubject.

3. If the PostgreSQL lookup fails:
   → return 500 Internal Server Error.
   → do not access object storage.

4. If the metadata is not found:
   → return 404 Not Found.

5. Delete the document content from object storage.

6. If the object is deleted successfully:
   → continue.

7. If the object is already absent:
   → treat deletion as successful and continue.

8. If object storage is unavailable or deletion otherwise fails:
   → return 503 Service Unavailable.

9. Delete the metadata from PostgreSQL using documentId + ownerSubject.

10. If metadata deletion fails:
    → return 500 Internal Server Error.
    → stale metadata may remain.
    → the request may be retried.

11. If metadata deletion succeeds:
    → return 204 No Content.
```

## Retry Behavior

The delete flow is retry-safe.

If object deletion succeeds but PostgreSQL deletion fails, a retry will encounter an already-missing object. That state is treated as successful object deletion, allowing the service to retry the metadata deletion.

Milestone 1 intentionally prefers stale metadata over retaining document bytes after the user has requested deletion.
