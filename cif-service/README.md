# MoneyBags CIF Service

The CIF Service manages the Customer Information File (CIF) for MoneyBags customers. It owns customer-profile data, generates a unique CIF ID, maintains the KYC status, links a CIF to an authenticated identity user, initiates KYC verification, and provides controlled customer data to downstream banking services.

The service owns the `CIFS` table and runs on port `8081`.

## Technology

- Java 25 and Spring Boot 4.1.0
- Spring Data JPA and Oracle
- Liquibase database migrations
- Eureka service discovery
- Spring Cloud LoadBalancer
- Spring RestClient
- Spring Security OAuth2 Resource Server with JWT
- Springdoc OpenAPI and Swagger UI
- Spring Boot Actuator and Prometheus metrics
- Maven

## Responsibilities

- Create customer CIF profiles.
- Generate a unique CIF ID.
- Store personal, contact, employment, and identification details.
- Maintain KYC status: `PENDING`, `APPROVED`, or `REJECTED`.
- Link an authenticated Identity Access Service user to a CIF.
- Initiate KYC after successful customer creation.
- Reset KYC to `PENDING` and initiate re-KYC after profile updates.
- Expose minimal data contracts to Credit Card, Deposit, Notification, and Statement services.
- Prevent duplicate email, mobile number, PAN, Aadhaar, and identity-user links.
- Enforce JWT-based access controls when security is enabled.

No other service should read or write the `CIFS` table directly. Inter-service communication happens through HTTP APIs only.

## Customer Data

The `CIFS` table contains:

| Column | Description |
|---|---|
| `CIF_ID` | Generated primary key and unique customer CIF identifier |
| `IDENTITY_USER_ID` | Optional unique Identity Access Service user ID linked to the CIF |
| `TENANT_ID` | Tenant identifier; defaults to `moneybags` |
| `FIRST_NAME` | Customer first name |
| `LAST_NAME` | Customer last name |
| `DOB` | Customer date of birth |
| `AGE` | Customer-provided age for the demo |
| `EMAIL` | Unique customer email |
| `MOBILE_NUMBER` | Unique mobile number |
| `ADDRESS` | Customer address |
| `EMPLOYMENT_TYPE` | `BUSINESS`, `SALARIED`, or `STUDENT` |
| `SALARY` | Salary/income amount |
| `KYC_STATUS` | `PENDING`, `APPROVED`, or `REJECTED` |
| `PAN_NUMBER` | Unique PAN number |
| `AADHAAR_NUMBER` | Unique Aadhaar number |
| `CREATED_AT` | Record creation timestamp |
| `UPDATED_AT` | Most recent update timestamp |

`AGE` is stored as provided by the customer for the current demo. It is not calculated from DOB.

## Business Rules

- Email, mobile number, PAN number, Aadhaar number, and identity-user ID must be unique.
- A `STUDENT` must not provide salary.
- A `BUSINESS` or `SALARIED` customer must provide salary greater than zero.
- A new CIF record starts with `kycStatus = PENDING`.
- Updating customer details resets KYC status to `PENDING`.
- Customer details remain available through GET APIs while KYC is pending, approved, or rejected.
- Credit Card and Deposit services must use `kycStatus` to decide whether a KYC-dependent operation may proceed.
- A customer with `REJECTED` status can correct details through the update API, which starts re-KYC.

## Prerequisites

- JDK 25
- Maven or the included Maven wrapper
- Oracle 19c or a compatible Oracle database
- Discovery Server on port `8761`
- API Gateway on port `8080` for gateway requests
- Identity Access Service for secured identity linking
- KYC Service for KYC initiation and final status callback

## Configuration

CIF Service supports a root `.env` file and normal operating-system environment variables.

```properties
CIF_DB_URL=jdbc:oracle:thin:@//localhost:1522/FREEPDB1
CIF_DB_USERNAME=moneybags
CIF_DB_PASSWORD=your-password

MONEYBAGS_DB_URL=jdbc:oracle:thin:@//localhost:1522/FREEPDB1
MONEYBAGS_DB_USERNAME=moneybags
MONEYBAGS_DB_PASSWORD=your-password

EUREKA_SERVER_URL=http://localhost:8761/eureka/
OAUTH2_ISSUER_URI=http://localhost:8093
OAUTH2_AUDIENCE=moneybags-api
SECURITY_ENABLED=true
```

CIF-specific variables take priority over the shared `MONEYBAGS_DB_*` variables.

The application loads `.env` from either the current working directory or the parent repository directory:

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:../.env[.properties]
```

Do not commit `.env`, passwords, database exports, logs, or generated `target/` files.

Liquibase applies the changelog from:

```text
src/main/resources/db/changelog/db.changelog-master.yaml
```

Hibernate validates the resulting Oracle schema using:

```text
ddl-auto=validate
```

It must not create or modify tables directly.

## Build and Run

From the repository root:

```powershell
mvn -pl cif-service -am clean package
mvn -pl cif-service spring-boot:run
```

Or from the `cif-service` directory:

```powershell
.\mvnw.cmd spring-boot:run
```

For complete integration testing, start services in this order:

1. Discovery Server
2. Identity Access Service, if security is enabled
3. CIF Service
4. API Gateway
5. KYC Service

Once started, use:

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- Health endpoint: `http://localhost:8081/actuator/health`
- Direct API base path: `http://localhost:8081/api/v1/cifs`
- Gateway API base path: `http://localhost:8080/api/v1/cifs`

The Gateway, Discovery Server, and CIF Service must all be running for Gateway requests.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/cifs` | Create a customer CIF profile |
| `GET` | `/api/v1/cifs/{cifId}` | Get complete CIF details |
| `PUT` | `/api/v1/cifs/{cifId}` | Update CIF details and initiate re-KYC |
| `PATCH` | `/api/v1/cifs/{cifId}/kyc-status` | Update final KYC status |
| `GET` | `/api/v1/cifs/{cifId}/credit-card-details` | Get limited data for Credit Card Service |
| `GET` | `/api/v1/cifs/{cifId}/deposit-creation-details` | Get limited data for Deposit Service |
| `GET` | `/api/v1/cifs/{cifId}/customer-contact-details` | Get limited contact data for Notification and Statement services |

## Example Workflow

### 1. Create a CIF Profile

```http
POST /api/v1/cifs
Content-Type: application/json
```

```json
{
  "firstName": "Aarav",
  "lastName": "Sharma",
  "dob": "1995-06-15",
  "age": 30,
  "email": "aarav.sharma@example.com",
  "number": "9876543210",
  "address": "21 Park Street, New Delhi",
  "employmentType": "SALARIED",
  "salary": 75000.00,
  "panNumber": "ABCDE1234F",
  "aadhaarNumber": "123456789012"
}
```

A successful request generates a CIF ID and creates the customer with:

```json
{
  "cifId": 1001,
  "kycStatus": "PENDING"
}
```

When a consumer JWT is supplied, CIF Service reads `user_id` and `tenant_id` from the token and stores the identity link with the new CIF record.

### 2. Link Identity and Initiate KYC

After the CIF transaction commits:

1. `CifServiceImpl` publishes `CifCreatedEvent`.
2. `KycInitiationListener` runs asynchronously after commit.
3. If an identity user is present, CIF calls Identity Access Service to link that user to the generated CIF ID.
4. CIF calls KYC Service through Eureka and LoadBalancer.
5. KYC Service receives:

```http
POST http://kyc-service/api/v1/kycs
```

The KYC request contains the required customer data, tenant ID, and CIF ID. It does not contain the CIF timestamps or age.

If KYC Service is unavailable, CIF remains stored with `PENDING` status and the failure is logged.

### 3. Get Complete CIF Details

```http
GET /api/v1/cifs/1001
```

This returns the complete CIF response for the specified customer.

### 4. Update CIF and Trigger Re-KYC

```http
PUT /api/v1/cifs/1001
Content-Type: application/json
```

The update request contains the same required fields as CIF creation.

After a successful update:

- Customer data is saved.
- KYC status is reset to `PENDING`.
- CIF publishes an event to initiate KYC again after commit.

### 5. Update Final KYC Status

KYC Service calls CIF after verification:

```http
PATCH /api/v1/cifs/1001/kyc-status
Content-Type: application/json
```

Approval:

```json
{
  "kycStatus": "APPROVED"
}
```

Rejection:

```json
{
  "kycStatus": "REJECTED"
}
```

KYC Service owns the verification decision. The frontend should not directly update CIF KYC status.

## Inter-Service Data Contracts

### Credit Card Service

```http
GET /api/v1/cifs/{cifId}/credit-card-details
```

Returns only:

```json
{
  "cifId": 1001,
  "age": 30,
  "employmentType": "SALARIED",
  "salary": 75000.00,
  "kycStatus": "APPROVED"
}
```

### Deposit Service

```http
GET /api/v1/cifs/{cifId}/deposit-creation-details
```

Returns only:

```json
{
  "cifId": 1001,
  "dob": "1995-06-15",
  "employmentType": "SALARIED",
  "kycStatus": "APPROVED"
}
```

### Notification and Statement Services

```http
GET /api/v1/cifs/{cifId}/customer-contact-details
```

Returns only:

```json
{
  "cifId": 1001,
  "firstName": "Aarav",
  "lastName": "Sharma",
  "email": "aarav.sharma@example.com",
  "number": "9876543210",
  "address": "21 Park Street, New Delhi"
}
```

Credit Card, Deposit, Notification, and Statement services must use these APIs and must not access the CIF database directly.

## Security

Security is enabled by default through:

```properties
SECURITY_ENABLED=true
```

CIF Service acts as an OAuth2 Resource Server and validates JWTs using:

- Issuer: `OAUTH2_ISSUER_URI`
- Audience: `OAUTH2_AUDIENCE`

The JWT converter maps:

- `roles` claim values to `ROLE_<role>`
- JWT scopes to `SCOPE_<scope>`

Examples of enforced access rules:

| Resource | Required authority |
|---|---|
| Swagger UI | `ROLE_BANK_ADMIN` |
| KYC status PATCH | `SCOPE_cif:service` |
| Credit Card, Deposit, and Contact data APIs | `SCOPE_cif:service` |
| General CIF GET | `SCOPE_cif:read` or `SCOPE_cif:admin` |
| CIF update | `SCOPE_cif:write` or `SCOPE_cif:admin` |
| CIF creation | `ROLE_BANK_ADMIN`, `SCOPE_cif:write`, or `SCOPE_cif:service` |

When security is enabled, `CifAuthorization` also verifies that a non-admin customer can access only the CIF ID stored in the JWT `customer_id` claim.

For local development only, security can be disabled:

```properties
SECURITY_ENABLED=false
```

This permits all requests and should not be used for a secured environment.

## Database Migrations

Liquibase creates and evolves only CIF-owned data.

1. `001-create-cifs-table.yaml` creates the `CIFS` table and primary/unique constraints.
2. `002-rename-number-to-mobile-number.yaml` replaces the Oracle-reserved `NUMBER` column name with `MOBILE_NUMBER`.
3. `003-change-verified-to-approved.yaml` changes the KYC status constraint to use `APPROVED`.
4. `004-add-age-to-cifs.yaml` adds the `AGE` column and its validation rule.
5. `005-add-identity-link.yaml` adds `IDENTITY_USER_ID`, `TENANT_ID`, and the unique identity-user constraint.

Do not edit Liquibase history manually, modify another service’s tables, or add cross-service foreign keys.

## Error Handling

| Situation | Expected result |
|---|---|
| Invalid request data | `400 Bad Request` |
| Invalid salary rule | `400 Bad Request` |
| Duplicate email, mobile, PAN, Aadhaar, or identity user | `409 Conflict` |
| CIF ID not found | `404 Not Found` |
| KYC Service unavailable after save | CIF remains `PENDING`; failure is logged |
| Unexpected error | `500 Internal Server Error` |

## Package Structure

```text
com.moneybags.cif
├── config
│   ├── AsyncConfig
│   ├── CifAuthorization
│   ├── ClientCredentialsTokenProvider
│   ├── OpenApiConfig
│   ├── RestClientConfig
│   └── SecurityConfig
├── controller
│   └── CifController
├── domain
│   ├── enums
│   │   ├── EmploymentType
│   │   └── KycStatus
│   └── event
│       └── CifCreatedEvent
├── dto
│   ├── request
│   └── response
├── entity
│   └── Cif
├── exception
├── integration
│   ├── IdentityServiceClient
│   ├── KycInitiationListener
│   └── KycServiceClient
├── repository
│   └── CifRepository
└── service
    ├── CifService
    └── impl
        └── CifServiceImpl
```
