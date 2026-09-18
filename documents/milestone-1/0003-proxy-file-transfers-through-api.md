# ADR 0003: Proxy File Transfers Through the API in Milestone 1

## Status

Accepted

## Context

A production-oriented document platform may allow clients to upload and download directly from object storage using short-lived signed URLs.

Direct object-storage transfer would reduce application bandwidth and request-thread usage, but it would also move parts of validation, upload-state management, and failure handling outside the application request path.

The primary purpose of Milestone 1 is to learn and implement backend fundamentals.

## Decision

During Milestone 1:

```text
client
  -> Spring Boot API
  -> private object storage
```

Both uploads and downloads pass through the Spring Boot application.

Clients do not access object storage directly.

## Consequences

### Positive

- Centralized authentication and authorization.
- Straightforward server-side file-size enforcement.
- Straightforward content inspection and file-type validation.
- Provides hands-on experience with streaming, multipart uploads, timeouts, and failure handling.
- Keeps private object storage fully behind the application boundary.

### Negative

- Application servers carry all upload/download bandwidth.
- Long file transfers consume application/network resources.
- This design scales less efficiently than direct signed-URL transfers.

## Future Direction

A later milestone may replace this flow with short-lived signed upload/download URLs.

That change should be driven by an identified scaling or architectural requirement rather than introduced preemptively.
