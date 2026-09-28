# ADR 0002: Store File Bytes in Object Storage

## Status

Accepted

## Context

Documents may be as large as 100 MiB and must survive application restarts and redeployments.

Possible storage locations included:

- relational database
- application local filesystem
- object/blob storage

The application filesystem is unsuitable because application instances may be restarted or replaced and local storage may be ephemeral.

Storing large binary objects directly in the relational database would tightly couple file capacity and database capacity and is unnecessary for this use case.

## Decision

Store document metadata in PostgreSQL and document bytes in private object storage.

For local development, an S3-compatible object store may be used. Production deployment is expected to use an object-storage service such as Amazon S3.

## Object Key

Milestone 1 does not persist a separate storage-key column.

The object key is derived deterministically from the immutable document ID, for example:

```text
documents/{documentId}
```

## Consequences

### Positive

- File storage scales independently from relational metadata.
- File bytes survive application restarts.
- Application instances remain stateless with respect to document content.
- Avoids coupling storage layout to user-provided filenames.

### Negative

- PostgreSQL and object storage do not share one transaction.
- The application must handle partial failure and orphaned-object scenarios.
- Local development requires an object-store dependency.
