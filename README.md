# Data Quality Platform

The Data Quality Platform is a learning and software-engineering project for building a full-stack data validation application in small, verifiable milestones.

## Current status

Milestone 1 provides the project foundation. Milestone 2 currently includes the PostgreSQL persistence foundation, the Dataset metadata vertical slice, and the Validation Profile vertical slice:

- a Java 21 and Spring Boot backend
- a React and TypeScript frontend
- a local PostgreSQL service through Docker Compose
- environment-backed backend datasource configuration
- Spring Data JPA, Bean Validation, and Flyway infrastructure
- a Flyway-managed `dataset` table
- a Flyway-managed `validation_profile` table related to its parent Dataset
- Dataset create, list, and detail REST endpoints
- Validation Profile create and list REST endpoints nested under a Dataset
- PostgreSQL Testcontainers repository and API integration tests
- backend and frontend tests and formatting checks
- a GitHub Actions workflow for repository checks

The backend connects to PostgreSQL at startup, applies the Dataset and Validation Profile migrations through Flyway, and validates the JPA mappings without generating schema changes. It exposes the Actuator health endpoint and the Dataset and Validation Profile endpoints documented below. The frontend remains a static application shell.

Dataset metadata can be created, listed, and retrieved. Validation Profiles can be created and listed for an existing Dataset. Dataset and profile updates or deletion, profile detail retrieval, pagination, validation rules, CSV uploads, validation runs, reports, authentication, and AI features are not implemented yet.

## Repository layout

```text
backend/                 Spring Boot application, Dataset and Validation Profile APIs, persistence, and Maven Wrapper
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

The PostgreSQL port is bound to `127.0.0.1` and is not exposed on external network interfaces.

Docker Compose reads `.env` automatically. Spring Boot does not, so load the same variables into the backend process environment before starting it. Spring Boot's standard `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` variables can override the composed local settings when needed.

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
- `GET /api/datasets`: list Datasets in creation order
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

The list endpoint returns `200 OK` with an array of the same response objects. It returns `[]` when no Datasets exist. The detail endpoint returns `200 OK` for an existing UUID. An unknown UUID returns `404 Not Found` with an `application/problem+json` response:

```json
{
  "title": "Dataset not found",
  "status": 404,
  "detail": "Dataset '47d9bea4-1130-4b9b-8fb3-ea23893d51e5' was not found.",
  "instance": "/api/datasets/47d9bea4-1130-4b9b-8fb3-ea23893d51e5"
}
```

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

- `test` starts isolated PostgreSQL Testcontainers and runs the Spring Boot integration tests.
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

- Milestone 2 remaining work: validation rule persistence and endpoints
- Milestone 3: CSV ingestion and validation-run lifecycle
- Milestone 4: deterministic validation rules and issue persistence
- Milestone 5: dataset, run, summary, and issue screens
- Milestone 6: report export, structured logs, runtime metrics, and final documentation

The detailed product scope and milestone definitions are maintained in `PROJECT_BRIEF.md`.
