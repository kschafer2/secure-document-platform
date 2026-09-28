# ADR 0001: Use UUIDv7 for Document IDs

## Status

Accepted

## Context

The document service needs a primary identifier that can also be exposed through the public API.

Options considered:

1. auto-incrementing `BIGINT`
2. internal `BIGINT` plus external UUID
3. a single UUIDv7 identifier

Auto-incrementing integer IDs are compact and efficient, but they are trivially enumerable and expose internal ordering.

Using separate internal and external identifiers avoids exposing database IDs but introduces two identities for the same document, an additional column, and an additional unique index.

UUIDv7 provides a single identifier usable by both the application and API while maintaining better index locality than fully random UUIDv4 identifiers.

## Decision

Use a UUIDv7 value as the document primary key and API identifier.

The application generates the identifier before persistence.

## Consequences

### Positive

- One document identifier throughout the system.
- Difficult to enumerate compared with sequential integers.
- Can be generated before database insertion.
- Suitable for distributed ID generation.
- Better B-tree locality than random UUIDv4 values.

### Negative

- Larger indexes than a `BIGINT` primary key.
- Less human-friendly when inspecting records manually.
- UUIDv7 exposes approximate creation time.
- UUIDv7 does not replace authorization checks.

## Security Note

Possession or knowledge of a valid document UUID never grants access. Every document operation must independently enforce ownership.
