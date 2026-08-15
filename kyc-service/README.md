# MoneyBags KYC Service

The KYC Service manages customer KYC records, supporting documents, document verification, final
approval or rejection, and synchronization of the final status with the CIF and Notification
services. It owns the `KYC` and `KYC_DOCUMENT` tables and runs on port `8082`.

## Technology

- Java 25 and Spring Boot 4.1
- Spring Data JPA and Oracle
- Liquibase database migrations
- Eureka service discovery
- Springdoc OpenAPI and Swagger UI

## Prerequisites

- JDK 25
- Maven (or the included Maven wrapper)
- Oracle 19c or a compatible Oracle database
- Discovery Server on port `8761` when Eureka is enabled
- CIF Service and Notification Service for final-decision callbacks

## Configuration

The current service configuration reads standard Spring datasource environment variables. Set them
before starting the application:

```powershell
$env:SPRING_DATASOURCE_URL = "url"
$env:SPRING_DATASOURCE_USERNAME = "username"
$env:SPRING_DATASOURCE_PASSWORD = "your-password"

$env:EUREKA_DEFAULT_ZONE = "http://localhost:8761/eureka/"
$env:CIF_SERVICE_URL = "http://CIF-SERVICE"
$env:NOTIFICATION_SERVICE_URL = "http://NOTIFICATION-SERVICE"
```

The default peer-service URLs use Eureka service names. To run without service discovery, point
`CIF_SERVICE_URL` and `NOTIFICATION_SERVICE_URL` at direct URLs such as
`http://localhost:8081` and `http://localhost:8090`, and disable discovery with command-line
properties:

```powershell
mvn -pl kyc-service spring-boot:run `
  "-Dspring-boot.run.arguments=--eureka.client.enabled=false --spring.cloud.discovery.enabled=false"
```

Do not commit real credentials. Liquibase applies the changelogs under
`src/main/resources/db/changelog`, and Hibernate validates the resulting schema with
`ddl-auto=validate`.

## Build and run

From the repository root:

```powershell
mvn -pl kyc-service -am clean package
mvn -pl kyc-service spring-boot:run
```

Or from `kyc-service`:

```powershell
.\mvnw.cmd spring-boot:run
```

Once started, use:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`
- Health: `http://localhost:8082/actuator/health`
- API base path: `http://localhost:8082/api/v1/kycs`
- Gateway API base path: `http://localhost:8080/api/v1/kycs`

The gateway route uses Eureka to resolve `kyc-service`; the Gateway, Discovery Server, and KYC
Service must all be running for gateway requests.

## API endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/kycs` | Create a KYC record |
| `GET` | `/api/v1/kycs/{kycId}` | Get a KYC record by ID |
| `GET` | `/api/v1/kycs?cifId={cifId}` | List KYC records for a CIF customer |
| `POST` | `/api/v1/kycs/{kycId}/documents` | Upload one or more documents |
| `GET` | `/api/v1/kycs/{kycId}/documents` | List document metadata |
| `GET` | `/api/v1/kycs/{kycId}/documents/{documentId}` | Download a document |
| `PATCH` | `/api/v1/kycs/{kycId}/documents/{documentId}/verification` | Verify a document |
| `PATCH` | `/api/v1/kycs/{kycId}/decision` | Record the final KYC decision |
| `POST` | `/api/v1/kycs/{kycId}/sync` | Retry a failed CIF synchronization |

## Example workflow

### 1. Create a KYC record

```http
POST /api/v1/kycs
Content-Type: application/json
```

```json
{
  "cifId": 1001,
  "firstName": "Aarav",
  "lastName": "Sharma",
  "dob": "1995-06-15",
  "number": "9876543210",
  "email": "aarav.sharma@example.com",
  "panNumber": "ABCDE1234F",
  "aadhaarNumber": "123456789012",
  "address": "21 Park Street, New Delhi",
  "employmentType": "SALARIED",
  "salary": 75000.00
}
```

`employmentType` accepts `BUSINESS`, `SALARIED`, or `STUDENT`. Salary must be positive for
`BUSINESS` and `SALARIED`, and must be omitted for `STUDENT`. New records begin in `PENDING`
status.

### 2. Upload documents

Send a `multipart/form-data` request in which each `documentTypes` entry corresponds to the file at
the same position:

```powershell
curl.exe -X POST "http://localhost:8082/api/v1/kycs/1/documents" `
  -F "documentTypes=PAN" `
  -F "files=@C:\documents\pan.pdf;type=application/pdf" `
  -F "documentTypes=AADHAAR" `
  -F "files=@C:\documents\aadhaar.png;type=image/png"
```

Supported document types are `PAN`, `AADHAAR`, `ADDRESS_PROOF`, and `SALARY_PROOF`. Files must be
PDF, PNG, or JPEG, cannot be empty, and cannot exceed 10 MB. A KYC can contain only one document of
each type.

### 3. Verify a document

```http
PATCH /api/v1/kycs/1/documents/1/verification
Content-Type: application/json
```

```json
{
  "status": "VERIFIED",
  "remarks": "Document details match the customer record",
  "verifiedBy": "reviewer@example.com"
}
```

The accepted results are `VERIFIED` and `MISMATCH`. A mismatch changes the parent KYC status to
`FLAGGED`.

### 4. Make a final decision

Approval:

```json
{
  "decision": "APPROVED",
  "reviewedBy": "reviewer@example.com"
}
```

Rejection requires a reason:

```json
{
  "decision": "REJECTED",
  "rejectionReason": "Submitted identity details do not match",
  "reviewedBy": "reviewer@example.com"
}
```

Send either body to `PATCH /api/v1/kycs/{kycId}/decision`. A final decision cannot be changed, and
documents cannot be uploaded or verified afterward. The service then attempts to update CIF and
send a notification. Notification failure does not roll back the decision; a failed CIF update can
be retried up to five times with `POST /api/v1/kycs/{kycId}/sync`.

## Database migrations

Liquibase creates and evolves only the KYC-owned schema:

1. `001-create-kyc-table.sql` creates `KYC` and its workflow/synchronization fields.
2. `002-create-kyc-document-table.sql` creates `KYC_DOCUMENT`, including its document BLOB.
3. `003-add-employment-snapshot-columns.sql` adds employment type and salary snapshots.

Do not edit another service's tables or add cross-service foreign keys.

## Postman

Import `postman/KYC-Service.postman_collection.json`. Its `baseUrl` defaults to
`http://localhost:8082`, and its folders cover health checks, the happy path, validation failures,
not-found behavior, document rules, and final-state restrictions.

## Tests

Run the module and its required upstream modules from the repository root:

```powershell
mvn -pl kyc-service -am test
```
