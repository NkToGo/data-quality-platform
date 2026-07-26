# Data Quality Platform

The Data Quality Platform is a learning and software-engineering project for building a full-stack data validation application in small, verifiable milestones.

## Current status

Milestone 1 provides the project foundation. Milestone 2 completed the PostgreSQL persistence foundation and the Dataset, Validation Profile, and Validation Rule persistence vertical slices. Milestone 3 is in progress with the SourceFile upload, Validation Run creation, and isolated CSV parser foundations:

- a Java 21 and Spring Boot backend
- a React and TypeScript frontend
- a local PostgreSQL service through Docker Compose
- environment-backed backend datasource configuration
- Spring Data JPA, Bean Validation, and Flyway infrastructure
- a Flyway-managed `dataset` table
- a Flyway-managed `validation_profile` table related to its parent Dataset
- a Flyway-managed `validation_rule` table related to its parent Validation Profile, with rule parameters stored as PostgreSQL `jsonb`
- a Flyway-managed `source_file` table that stores upload metadata and private file contents
- a Flyway-managed V5 `validation_run` table related to its Dataset, SourceFile, and Validation Profile
- Dataset create, list, and detail REST endpoints
- Validation Profile create and list REST endpoints nested under a Dataset
- Validation Rule create and list REST endpoints nested under a Validation Profile
- a Dataset-nested multipart CSV upload endpoint with a SHA-256 checksum
- a SourceFile-nested endpoint that creates a pending Validation Run
- an isolated, deterministic UTF-8 CSV parser with focused unit tests
- PostgreSQL Testcontainers repository and API integration tests
- backend and frontend tests and formatting checks
- a GitHub Actions workflow for repository checks

The backend connects to PostgreSQL at startup. Flyway is the sole schema owner and applies the Dataset, Validation Profile, Validation Rule, SourceFile, and Validation Run migrations. Hibernate validates the JPA mappings with `spring.jpa.hibernate.ddl-auto=validate` and does not generate schema changes. The backend exposes the Actuator health endpoint and the Dataset, Validation Profile, Validation Rule, SourceFile upload, and Validation Run creation endpoints documented below. The frontend remains a static application shell.

Dataset metadata can be created, listed, and retrieved. Validation Profiles can be created and listed for an existing Dataset. Validation Rules can be created and listed for an existing Validation Profile. CSV files can be uploaded for an existing Dataset, and the backend stores their metadata, exact bytes, and SHA-256 checksums. A pending Validation Run can be created for a SourceFile and a Validation Profile from the same Dataset. An isolated CSV parser now defines and tests deterministic parsing behavior, but no endpoint or service reads stored SourceFile bytes for parsing yet. Parser error persistence, Validation Run processing transitions, rule execution, and rule-specific parameter validation are not implemented. Dataset, profile, rule, SourceFile, and Validation Run updates or deletion, profile, rule, SourceFile, and Validation Run detail retrieval, Validation Run listing, pagination, reports, authentication, and AI features are also not implemented yet.

## Repository layout

```text
backend/                 Spring Boot application, persistence, REST APIs, ingestion foundation, and Maven Wrapper
frontend/                React, TypeScript, and Vite application
.github/workflows/       Continuous integration checks
compose.yaml             Local PostgreSQL service
.env.example             Example local database and datasource configuration
PROJECT_BRIEF.md         Product scope and milestone definition
```

## Prerequisites

- Java Development Kit 21
- Node.js 24 LTS and npm 11
- Docker with Docker Compose, required for the local database and backend integration tests

Maven does not need to be installed globally because the backend includes the Maven Wrapper.

## Environment setup

Create an untracked local environment file and replace the example password before starting PostgreSQL.

Unix-like shells:

```sh
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

The available variables are:

- `POSTGRES_HOST`: backend database host, defaults to `localhost`
- `POSTGRES_DB`: database name, defaults to `data_quality`
- `POSTGRES_USER`: database user, defaults to `data_quality`
- `POSTGRES_PASSWORD`: required local password
- `POSTGRES_PORT`: host port, defaults to `5432`
- `SOURCE_FILE_MAX_SIZE`: maximum uploaded file size, defaults to `10MB`
- `SOURCE_FILE_MAX_REQUEST_SIZE`: multipart request limit, defaults to `11MB`

The PostgreSQL port is bound to `127.0.0.1` and is not exposed on external network interfaces.

Docker Compose reads `.env` automatically. Spring Boot does not, so load the same variables into the backend process environment before starting it. Spring Boot's standard `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` variables can override the composed local settings when needed. The multipart request limit includes headers and must remain greater than the SourceFile size limit.

## Run locally

Start PostgreSQL from the repository root:

```sh
docker compose up -d postgres
docker compose ps
```

Stop PostgreSQL without deleting its persistent volume:

```sh
docker compose down
```

Run the backend on Unix-like systems after loading the root `.env`:

```sh
cd backend
set -a
. ../.env
set +a
./mvnw spring-boot:run
```

Run the backend on Windows PowerShell after loading the root `.env`:

```powershell
cd backend
Get-Content ..\.env |
  Where-Object { $_ -match '^[^#\s][^=]*=' } |
  ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
  }
.\mvnw.cmd spring-boot:run
```

The backend listens on `http://localhost:8080`. Its health endpoint is `http://localhost:8080/actuator/health`. A missing or incorrect database password causes startup to fail when Flyway connects.

## Dataset API

The current API manages Dataset metadata only. A Dataset contains a generated UUID, a required name, an optional description, and a creation timestamp.

Available endpoints:

- `POST /api/datasets`: create a Dataset
- `GET /api/datasets`: list Datasets by `createdAt` ascending, then `id` ascending
- `GET /api/datasets/{datasetId}`: retrieve one Dataset by UUID

Create request:

```http
POST /api/datasets
Content-Type: application/json
```

```json
{
  "name": "Customer import",
  "description": "Customer data received for validation"
}
```

The name is required, must contain a non-whitespace character, and has a maximum length of 255 characters. The description may be omitted or set to `null` and has a maximum length of 2,000 characters. Dataset names do not need to be unique.

A successful create request returns `201 Created`, a `Location` header for the new Dataset, and a response such as:

```json
{
  "id": "47d9bea4-1130-4b9b-8fb3-ea23893d51e5",
  "name": "Customer import",
  "description": "Customer data received for validation",
  "createdAt": "2026-07-20T12:34:56.123456Z"
}
```

The list endpoint returns `200 OK` with an array of the same response objects ordered by `createdAt` ascending, then `id` ascending. It returns `[]` when no Datasets exist. The detail endpoint returns `200 OK` for an existing UUID. An unknown UUID returns `404 Not Found` with an `application/problem+json` response:

```json
{
  "title": "Dataset not found",
  "status": 404,
  "detail": "Dataset '47d9bea4-1130-4b9b-8fb3-ea23893d51e5' was not found.",
  "instance": "/api/datasets/47d9bea4-1130-4b9b-8fb3-ea23893d51e5"
}
```

A malformed Dataset UUID returns `400 Bad Request`.

After starting PostgreSQL and the backend, smoke-test the API from a Unix-like shell:

```sh
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"name":"Customer import","description":"Manual smoke test"}' \
  http://localhost:8080/api/datasets

curl --fail-with-body http://localhost:8080/api/datasets
curl --fail-with-body http://localhost:8080/api/datasets/REPLACE_WITH_DATASET_ID
```

Windows PowerShell:

```powershell
$created = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/datasets `
  -ContentType application/json `
  -Body '{"name":"Customer import","description":"Manual smoke test"}'

Invoke-RestMethod http://localhost:8080/api/datasets
Invoke-RestMethod "http://localhost:8080/api/datasets/$($created.id)"
```

## SourceFile upload API

A SourceFile belongs to one Dataset. The backend stores the exact uploaded bytes privately in PostgreSQL together with a generated UUID, the parent Dataset UUID, the stored filename basename, the submitted content type, the byte count, a SHA-256 checksum, and an upload timestamp.

Available endpoint:

- `POST /api/datasets/{datasetId}/files`: upload one CSV file for a Dataset

The request must use `multipart/form-data` with one part named `file`. Files must be nonempty, no larger than the configured maximum, and have a basename of at most 255 characters ending in `.csv` case-insensitively. Submitted path components are removed before the filename is stored.

The submitted MIME type is recorded as metadata, has a maximum length of 255 characters, and is not treated as proof that the content is valid CSV. Missing or blank MIME types are stored as `application/octet-stream`. Upload admission does not parse the content or perform semantic validation.

A successful upload returns `201 Created` without a `Location` header because no SourceFile detail endpoint exists. The response contains only metadata:

```json
{
  "id": "54985ec5-103b-4d2b-95f3-0b57e2d74336",
  "datasetId": "47d9bea4-1130-4b9b-8fb3-ea23893d51e5",
  "originalFilename": "customers.csv",
  "contentType": "text/csv",
  "sizeBytes": 128,
  "sha256": "a7b64b6df8f231b5f111e4e7bdba8af0c81c8639f33d48c7206ec66a10cb8ef0",
  "uploadedAt": "2026-07-25T12:34:56.123456Z"
}
```

The checksum is a 64-character lowercase hexadecimal SHA-256 value calculated over the exact stored bytes. Duplicate filenames and duplicate checksums are allowed. File contents are never returned by the API.

An invalid upload returns `400 Bad Request`. An upload over the configured limit returns `413 Payload Too Large`. A malformed Dataset UUID returns `400 Bad Request`. A valid but unknown Dataset UUID returns the existing Dataset `404 Not Found` Problem Details response with the upload path as its `instance`. Failed requests do not persist a SourceFile.

After creating a Dataset, upload a CSV from a Unix-like shell:

```sh
curl --fail-with-body \
  --request POST \
  --form 'file=@customers.csv;type=text/csv' \
  "http://localhost:8080/api/datasets/REPLACE_WITH_DATASET_ID/files"
```

Windows PowerShell, continuing from the Dataset API example:

```powershell
$csvPath = Join-Path $env:TEMP "customers.csv"
[System.IO.File]::WriteAllText(
  $csvPath,
  "email,age`nalice@example.com,30`n",
  [System.Text.UTF8Encoding]::new($false)
)

$expectedHash = (Get-FileHash $csvPath -Algorithm SHA256).Hash.ToLowerInvariant()

$uploaded = curl.exe --silent --show-error `
  --request POST `
  --form "file=@$csvPath;type=text/csv" `
  "http://localhost:8080/api/datasets/$($created.id)/files" |
  ConvertFrom-Json

$uploaded
$expectedHash
```

No SourceFile list, detail, download, or deletion endpoint is implemented. Uploading a SourceFile does not parse it or automatically create a Validation Run.

## CSV parser foundation

The backend includes an isolated in-memory CSV parser with focused unit tests. It is not a Spring service and is not called by an API, SourceFile service, or Validation Run workflow yet. Stored SourceFile bytes remain private and are not read for processing. Validation Runs therefore remain `PENDING`, and parser failures are not persisted.

The parser contract is:

- input must be valid UTF-8 and may begin with one UTF-8 byte-order mark
- comma is the fixed delimiter, comments and delimiter detection are not supported
- LF, CRLF, and lone CR record separators are accepted
- the first logical record is the required header, and a header-only file is valid
- header names are preserved exactly, must not be blank, and must be unique with case-sensitive comparison
- RFC-style quoted fields, doubled quotes, embedded commas, and embedded line endings are supported
- field whitespace is preserved and empty fields are represented as empty strings
- blank records are data records rather than ignored input
- each data record must contain exactly the same number of fields as the header
- empty or BOM-only input, invalid UTF-8, invalid headers, inconsistent field counts, and malformed quoting are rejected
- logical record numbering is 1-based and includes the header, so the first data record is record 2

The parser returns immutable ordered headers and rows. Embedded newlines inside a quoted field do not increment the logical record number. Parser behavior is intentionally isolated from persistence and Validation Rule execution in this commit.

## Validation Profile API

A Validation Profile belongs to one Dataset and contains a generated UUID, the parent Dataset UUID, a required name, and a creation timestamp.

Available endpoints:

- `POST /api/datasets/{datasetId}/profiles`: create a Validation Profile for a Dataset
- `GET /api/datasets/{datasetId}/profiles`: list a Dataset's Validation Profiles

Create request:

```http
POST /api/datasets/47d9bea4-1130-4b9b-8fb3-ea23893d51e5/profiles
Content-Type: application/json
```

```json
{
  "name": "Default validation"
}
```

The name is required, must contain a non-whitespace character, and has a maximum length of 255 characters. Profile names do not need to be unique, including within the same Dataset.

A successful create request returns `201 Created` without a `Location` header because a profile detail endpoint is not implemented. The response contains only the persisted profile metadata:

```json
{
  "id": "6dc81327-2a6b-46c9-9a09-43a64f989ac2",
  "datasetId": "47d9bea4-1130-4b9b-8fb3-ea23893d51e5",
  "name": "Default validation",
  "createdAt": "2026-07-21T12:34:56.123456Z"
}
```

The list endpoint returns `200 OK` with profiles ordered by `createdAt` ascending and then by `id` ascending. It returns `[]` when the Dataset exists but has no profiles.

Both endpoints require the parent Dataset to exist. A valid but unknown Dataset UUID returns `404 Not Found` with an `application/problem+json` response. The `instance` contains the requested nested resource path:

```json
{
  "title": "Dataset not found",
  "status": 404,
  "detail": "Dataset '47d9bea4-1130-4b9b-8fb3-ea23893d51e5' was not found.",
  "instance": "/api/datasets/47d9bea4-1130-4b9b-8fb3-ea23893d51e5/profiles"
}
```

A malformed Dataset UUID returns `400 Bad Request`. An unknown Dataset does not produce an empty profile list and a failed create request does not write a profile.

After creating a Dataset, smoke-test the Validation Profile API from a Unix-like shell. Replace the example value with the created Dataset UUID:

```sh
DATASET_ID=REPLACE_WITH_DATASET_ID

curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"name":"Default validation"}' \
  "http://localhost:8080/api/datasets/$DATASET_ID/profiles"

curl --fail-with-body \
  "http://localhost:8080/api/datasets/$DATASET_ID/profiles"
```

Windows PowerShell, continuing from the Dataset API example above:

```powershell
$profile = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/datasets/$($created.id)/profiles" `
  -ContentType application/json `
  -Body '{"name":"Default validation"}'

$profile
Invoke-RestMethod "http://localhost:8080/api/datasets/$($created.id)/profiles"
```

## Validation Rule API

A Validation Rule belongs to one Validation Profile. It contains a generated UUID, the parent Profile UUID, a field name, a rule type, a parameters object, a severity, and an enabled flag.

Available endpoints:

- `POST /api/profiles/{profileId}/rules`: create a Validation Rule for a Profile
- `GET /api/profiles/{profileId}/rules`: list a Profile's Validation Rules

Create request:

```http
POST /api/profiles/6dc81327-2a6b-46c9-9a09-43a64f989ac2/rules
Content-Type: application/json
```

```json
{
  "fieldName": "email",
  "ruleType": "REQUIRED_FIELD",
  "parameters": {},
  "severity": "ERROR",
  "enabled": true
}
```

The `fieldName` is required, must contain a non-whitespace character, and has a maximum length of 255 characters. The supported rule types are `REQUIRED_FIELD`, `DATA_TYPE`, `UNIQUENESS`, `NUMERIC_RANGE`, and `DATE_FORMAT`. Severity must be `ERROR` or `WARNING`. The `enabled` value is required and must be a Boolean.

`parameters` is required and must be a JSON object. Empty and nested objects are accepted and stored in the V3 migration's PostgreSQL `jsonb` column. The API treats this object as opaque configuration in this milestone. It does not yet validate rule-specific parameter names or values, and it does not execute rules. Missing or null required values, unsupported enum values, scalar or array parameters, and invalid field names return `400 Bad Request`. Duplicate or overlapping rules are allowed.

A successful create request returns `201 Created` without a `Location` header because a rule detail endpoint is not implemented. The response contains only the persisted rule configuration:

```json
{
  "id": "32388666-f9dc-4500-96f8-d49f7bf75315",
  "profileId": "6dc81327-2a6b-46c9-9a09-43a64f989ac2",
  "fieldName": "email",
  "ruleType": "REQUIRED_FIELD",
  "parameters": {},
  "severity": "ERROR",
  "enabled": true
}
```

The list endpoint returns `200 OK` with rules ordered by `id` ascending in PostgreSQL. This deterministic order does not represent creation or execution order. It returns `[]` when the Profile exists but has no rules.

Both endpoints require the parent Validation Profile to exist. A valid but unknown Profile UUID returns `404 Not Found` with an `application/problem+json` response:

```json
{
  "title": "Validation Profile not found",
  "status": 404,
  "detail": "Validation Profile '6dc81327-2a6b-46c9-9a09-43a64f989ac2' was not found.",
  "instance": "/api/profiles/6dc81327-2a6b-46c9-9a09-43a64f989ac2/rules"
}
```

A malformed Profile UUID returns `400 Bad Request`. An unknown Profile does not produce an empty rule list and a failed create request does not write a rule.

After creating a Validation Profile, smoke-test the Validation Rule API from a Unix-like shell. Replace the example value with the created Profile UUID:

```sh
PROFILE_ID=REPLACE_WITH_PROFILE_ID

curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"fieldName":"email","ruleType":"REQUIRED_FIELD","parameters":{},"severity":"ERROR","enabled":true}' \
  "http://localhost:8080/api/profiles/$PROFILE_ID/rules"

curl --fail-with-body \
  "http://localhost:8080/api/profiles/$PROFILE_ID/rules"
```

Windows PowerShell, continuing from the Validation Profile API example above:

```powershell
$rule = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/profiles/$($profile.id)/rules" `
  -ContentType application/json `
  -Body '{"fieldName":"email","ruleType":"REQUIRED_FIELD","parameters":{},"severity":"ERROR","enabled":true}'

$rule
Invoke-RestMethod "http://localhost:8080/api/profiles/$($profile.id)/rules"
```

## Validation Run API

A Validation Run belongs to one Dataset, one SourceFile, and one Validation Profile. The SourceFile and Validation Profile must belong to the same Dataset. The Dataset UUID is derived from the SourceFile and is not accepted from the client.

Available endpoint:

- `POST /api/files/{fileId}/validation-runs`: create a pending Validation Run

The request must use `application/json` and contain only the Validation Profile UUID:

```http
POST /api/files/54985ec5-103b-4d2b-95f3-0b57e2d74336/validation-runs
Content-Type: application/json
```

```json
{
  "profileId": "6dc81327-2a6b-46c9-9a09-43a64f989ac2"
}
```

The `profileId` value is required. A missing, null, or malformed value returns `400 Bad Request`.

A successful request returns `201 Created` without a `Location` header because a Validation Run detail endpoint is not implemented. The response contains exactly 12 fields:

```json
{
  "id": "1d97a9a7-eb56-44da-a566-a9630f23cbcb",
  "datasetId": "47d9bea4-1130-4b9b-8fb3-ea23893d51e5",
  "sourceFileId": "54985ec5-103b-4d2b-95f3-0b57e2d74336",
  "profileId": "6dc81327-2a6b-46c9-9a09-43a64f989ac2",
  "status": "PENDING",
  "totalRows": 0,
  "validRows": 0,
  "invalidRows": 0,
  "issueCount": 0,
  "startedAt": null,
  "finishedAt": null,
  "failureReason": null
}
```

The defined lifecycle statuses are `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`. This slice creates only `PENDING` Runs. The four counters start at zero, and the processing timestamps and failure reason start as `null`. Multiple Runs for the same SourceFile and Validation Profile are allowed and receive independent UUIDs.

A valid but unknown SourceFile UUID returns `404 Not Found` with an `application/problem+json` response:

```json
{
  "title": "Source file not found",
  "status": 404,
  "detail": "Source file '54985ec5-103b-4d2b-95f3-0b57e2d74336' was not found.",
  "instance": "/api/files/54985ec5-103b-4d2b-95f3-0b57e2d74336/validation-runs"
}
```

A valid but unknown Validation Profile UUID returns the existing `Validation Profile not found` response with the Validation Run request path as its `instance`. A malformed SourceFile UUID returns `400 Bad Request`.

If the SourceFile and Validation Profile belong to different Datasets, the request returns `409 Conflict`:

```json
{
  "title": "Validation Run parent mismatch",
  "status": 409,
  "detail": "Source file '54985ec5-103b-4d2b-95f3-0b57e2d74336' and Validation Profile '6dc81327-2a6b-46c9-9a09-43a64f989ac2' belong to different Datasets.",
  "instance": "/api/files/54985ec5-103b-4d2b-95f3-0b57e2d74336/validation-runs"
}
```

Failed requests do not persist a Validation Run.

After uploading a SourceFile and creating a Validation Profile, create a Validation Run from a Unix-like shell:

```sh
FILE_ID=REPLACE_WITH_SOURCE_FILE_ID
PROFILE_ID=REPLACE_WITH_PROFILE_ID

curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data "{\"profileId\":\"$PROFILE_ID\"}" \
  "http://localhost:8080/api/files/$FILE_ID/validation-runs"
```

Windows PowerShell, continuing from the SourceFile and Validation Profile examples:

```powershell
$run = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/files/$($uploaded.id)/validation-runs" `
  -ContentType application/json `
  -Body (@{ profileId = $profile.id } | ConvertTo-Json)

$run
```

No Validation Run list, detail, update, deletion, processing, or retry endpoint is implemented. Run creation does not parse CSV content, execute Validation Rules, generate Validation Issues, calculate summaries, or transition the Run beyond `PENDING`.

## Persistence relationships

Validation Profiles and SourceFiles require an existing Dataset, Validation Rules require an existing Validation Profile, and Validation Runs require an existing Dataset, SourceFile, and Validation Profile. Validation Run creation also requires the SourceFile and Validation Profile to belong to the same Dataset. All foreign keys use `ON DELETE RESTRICT`, and no cascading deletion is configured. If rows are removed directly during local cleanup, delete Validation Runs first, then Validation Rules and SourceFiles, then Validation Profiles, and finally Datasets.

Run the frontend on Unix-like systems:

```sh
cd frontend
npm ci
npm run dev
```

Run the frontend on Windows PowerShell:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

The Vite development server prints its local URL, normally `http://localhost:5173`.

## Backend commands

Run these commands from `backend/`. Replace `./mvnw` with `.\mvnw.cmd` on Windows.

```sh
./mvnw test
./mvnw package
./mvnw spotless:check
./mvnw spotless:apply
./mvnw verify
```

- `test` runs focused unit tests and starts isolated PostgreSQL Testcontainers for the Spring Boot integration tests.
- `package` runs tests and creates the executable JAR in `backend/target/`.
- `spotless:check` verifies Java formatting.
- `spotless:apply` formats Java source files.
- `verify` runs the complete backend build, including tests and the formatting check.

Docker must be running for `test`, `package`, and `verify`. The integration test uses its own disposable database and does not use the local Compose database or `.env`.

## Frontend commands

Run these commands from `frontend/`. On Windows PowerShell, use `npm.cmd` if the PowerShell execution policy blocks `npm.ps1`.

```sh
npm ci
npm run dev
npm run test
npm run test:watch
npm run lint
npm run format:check
npm run format
npm run build
npm run check
```

- `test` runs Vitest once and exits.
- `lint` runs ESLint with warnings treated as failures.
- `format:check` checks formatting without changing files.
- `format` applies Prettier formatting.
- `build` type-checks the application and creates a production build.
- `check` runs linting, formatting verification, tests, and the production build.

## Compose validation

Validate the committed configuration without creating a local `.env` file:

```sh
docker compose --env-file .env.example config --quiet
```

## Continuous integration

The GitHub Actions workflow runs three independent jobs on pushes and pull requests:

- backend Maven verification on Java 21 with an isolated PostgreSQL Testcontainer
- frontend install, lint, formatting, test, and build checks on Node.js 24
- Docker Compose configuration validation

## Planned milestones

- Milestone 2: complete, with PostgreSQL persistence and Dataset, Validation Profile, and Validation Rule REST vertical slices
- Milestone 3: in progress, with SourceFile upload, pending Validation Run creation, and the isolated CSV parser foundation implemented, while parser integration and processing transitions remain planned
- Milestone 4: deterministic validation rules and issue persistence
- Milestone 5: dataset, run, summary, and issue screens
- Milestone 6: report export, structured logs, runtime metrics, and final documentation

The detailed product scope and milestone definitions are maintained in `PROJECT_BRIEF.md`.
