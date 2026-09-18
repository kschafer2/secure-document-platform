# Secure Document Platform — Milestone 1 API

## API Conventions

- All endpoints require an authenticated user.
- Unauthenticated requests return `401 Unauthorized`.
- Document ownership is derived from the authenticated identity.
- The API never accepts `owner_subject` or an equivalent owner identifier from the client.
- For document-specific operations, a document that does not exist and a document owned by another user are both exposed as `404 Not Found`.
- JSON is used for metadata responses.
- Raw file bytes are returned directly for document content downloads.
- Object storage remains private and is not exposed directly to clients.

---

## Upload Document

### Request

```http
POST /documents
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

The multipart request contains one file.

The server derives or validates:

- `owner_subject` from authenticated identity
- `document_id` as UUIDv7
- `file_size_bytes` from the actual parsed file part
- canonical MIME type from file-content inspection
- validated filename
- upload timestamp from the database

The server does not trust client-supplied size information, the overall HTTP `Content-Length`, or client-supplied MIME type as authoritative document metadata. The 100 MiB business limit applies to the actual file part, not the multipart request envelope.

### Behavior

- Reject empty files with `400 Bad Request` and application error code `EMPTY_FILE`.
- Reject files larger than 100 MiB with `413 Payload Too Large` and application error code `FILE_TOO_LARGE`.
- Reject unsupported or unidentifiable file types with `415 Unsupported Media Type` and application error code `UNSUPPORTED_FILE_TYPE`.
- Treat the server-detected canonical MIME type as authoritative.
- If the detected MIME type is supported, accept the upload even when the client-provided MIME type or filename extension disagrees. Preserve the validated filename unchanged and persist the detected canonical MIME type.
- Validate the filename and reject invalid names rather than rewriting them.
- Reject filenames that are missing, empty, whitespace-only, longer than 255 characters, contain `/` or `\`, contain NUL or other control characters, or are exactly `.` or `..`.
- If the filename already exists for the same owner, reject the upload with `409 Conflict`.
- Persist metadata in PostgreSQL.
- Store document bytes in private object storage.

Validation uses first-failure precedence in this order:

```text
file size
    ↓
filename
    ↓
detected MIME type
    ↓
duplicate filename
```

The HTTP/multipart layer separately enforces a coarse overall request-size limit above the 100 MiB file limit to allow for multipart framing overhead.

### Success Response

```http
HTTP/1.1 201 Created
Location: /documents/{documentId}
```

No response body is required.

### Filename Conflict

If the authenticated owner already has a document with the same case-sensitive filename:

```http
HTTP/1.1 409 Conflict
Content-Type: application/json
```

```json
{
  "code": "DOCUMENT_NAME_CONFLICT",
  "message": "A document with this filename already exists."
}
```

The service does not automatically rename the document.

---

## List My Documents

### Request

```http
GET /documents
Accept: application/json
Authorization: Bearer <token>
```

### Success Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

Example:

```json
[
  {
    "documentId": "0199c123-...",
    "fileName": "report.pdf",
    "fileSizeBytes": 123456,
    "contentType": "application/pdf",
    "uploadedAt": "2026-09-18T18:00:00Z"
  }
]
```

If the authenticated user has no documents:

```json
[]
```

### Notes

- The endpoint returns only documents owned by the authenticated user.
- `owner_subject` is not included in the response.
- Pagination is out of scope for Milestone 1.
- If the PostgreSQL lookup fails, return `500 Internal Server Error`.

---

## Get Document Metadata

### Request

```http
GET /documents/{documentId}
Accept: application/json
Authorization: Bearer <token>
```

### Success Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

Example:

```json
{
  "documentId": "0199c123-...",
  "fileName": "report.pdf",
  "fileSizeBytes": 123456,
  "contentType": "application/pdf",
  "uploadedAt": "2026-09-18T18:00:00Z"
}
```

### Not Found / Not Owned

Return:

```http
404 Not Found
```

for both:

- nonexistent document ID
- existing document owned by another user

If the ownership-scoped PostgreSQL lookup fails, return `500 Internal Server Error`.

---

## Download Document Content

### Request

```http
GET /documents/{documentId}/content
Authorization: Bearer <token>
```

### Success Response

```http
HTTP/1.1 200 OK
Content-Type: <stored canonical MIME type>
Content-Disposition: attachment; filename="<validated filename>"
```

The response body contains the raw file bytes.

### Notes

- Do not wrap document bytes in JSON or Base64.
- The application streams the content from private object storage through the API.
- The user must own the requested document.

### Not Found / Not Owned

Return:

```http
404 Not Found
```

for both:

- nonexistent document ID
- existing document owned by another user

If the ownership-scoped PostgreSQL lookup fails, return `500 Internal Server Error` and do not access object storage.

If ownership-scoped metadata exists but the corresponding object is missing from object storage, return `500 Internal Server Error`.

If object storage is unavailable or fails before streaming begins, return `503 Service Unavailable`.

If streaming fails after the `200 OK` response has already been committed, terminate the incomplete stream and record the failure. The client may retry the full GET. Milestone 1 does not support resumable downloads or automatic midstream retry.

---

## Delete Document

### Request

```http
DELETE /documents/{documentId}
Authorization: Bearer <token>
```

### Success Response

```http
HTTP/1.1 204 No Content
```

No response body is returned.

### Behavior

- Verify the authenticated user owns the document.
- Permanently delete the document according to the Milestone 1 deletion workflow.
- Soft delete and recovery are out of scope.

### Not Found / Not Owned

Return:

```http
404 Not Found
```

for both:

- nonexistent document ID
- existing document owned by another user

If the ownership-scoped PostgreSQL lookup fails, return `500 Internal Server Error` and do not access object storage.

If object storage is unavailable or deletion fails before metadata deletion, return `503 Service Unavailable`.

If object deletion succeeds but PostgreSQL metadata deletion fails, return `500 Internal Server Error`. The delete request may be retried; an already-missing object is treated as a successful object-deletion state during retry.

---

## Milestone 1 Endpoint Summary

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/documents` | Upload one document |
| `GET` | `/documents` | List authenticated user's documents |
| `GET` | `/documents/{documentId}` | Get document metadata |
| `GET` | `/documents/{documentId}/content` | Download raw document content |
| `DELETE` | `/documents/{documentId}` | Permanently delete a document |
