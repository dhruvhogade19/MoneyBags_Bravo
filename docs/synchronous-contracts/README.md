# Moneybags synchronous contract package

This directory contains documentation and schema structure only; it does not implement the missing services.

## Artifacts

- `Moneybags_Complete_Synchronous_API_Contract.md` - canonical endpoints, workflows, service ownership, DTO references, and table blueprints.
- `Moneybags_Oracle_Schema_Ownership_and_Table_Blueprint.md` - database-per-service Oracle table structure.
- `moneybags-synchronous-contract.json` - complete machine-readable service/API/DTO/database catalog.
- `openapi/*.openapi.json` - one OpenAPI 3.0.3 definition per business service.
- `moneybags-synchronous-dependency-architecture.mmd` - Mermaid dependency diagram.
- `schema-images/*.png` - one high-resolution Oracle schema diagram for each business service.
- `contract-audit.json` - alignment and DOCX structural audit results.
- `Moneybags_Complete_Synchronous_API_Contract.docx` - formatted Word reference.

Final contract audit: **PASS**; 12 services, 128 endpoints, 132 named API schemas, and 39 resolved synchronous dependency calls.
