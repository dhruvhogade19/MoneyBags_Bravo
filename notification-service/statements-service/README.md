# Statements Service

Statements aggregates Accounting ledger entries with Deposit transaction balance projections. It stores immutable
statement headers, line snapshots, and a downloadable PDF in its own Oracle schema.

- `POST /internal/v1/statements/generate` (service-only; not an EOD endpoint)
- `GET /api/v1/statements/{statementId}`
- `GET /api/v1/statements/{statementId}/download`

Generation rejects a statement when the Accounting aggregation and Deposit balance projection do not match.
