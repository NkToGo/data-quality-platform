# Data Quality Platform — Project Brief

## 1. Purpose

The Data Quality Platform is a full-stack application for ingesting structured data, validating it against configurable rules, tracking processing runs, and presenting data-quality issues in a clear dashboard.

The project focuses on practical software engineering:

- reliable backend services
- explicit data models
- database migrations
- testable validation logic
- transparent processing states
- useful error handling
- observable runtime behavior
- a small but complete frontend
- reproducible local setup

## 2. Core user flow

1. A user creates a dataset.
2. The user uploads a CSV file.
3. The system creates a validation run.
4. The file is parsed and validated.
5. The run moves through `PENDING`, `PROCESSING`, `COMPLETED`, or `FAILED`.
6. Validation issues are stored with row, field, rule, severity, and message.
7. The user views the run summary and issue details in the frontend.
8. The user exports the validation report as JSON or CSV.

## 3. MVP scope

The first usable version includes:

- dataset creation
- CSV upload
- PostgreSQL persistence
- validation runs with explicit status
- five validation rule types:
  - required field
  - data type
  - uniqueness
  - numeric range
  - date format
- issue persistence
- run summary
- issue list with filtering by severity and field
- report export
- React/TypeScript dashboard
- backend and frontend tests
- Docker Compose
- GitHub Actions
- health endpoint
- structured logging

## 4. Non-goals for the MVP

The MVP will not include:

- microservices
- Kubernetes
- autonomous AI actions
- automatic data correction
- complex authentication or multi-tenancy
- real-time streaming
- a message broker
- arbitrary user-defined code execution
- production cloud deployment

These may be considered only after the MVP is stable.

## 5. Technology choices

### Backend

- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway
- Bean Validation
- OpenAPI
- JUnit 5
- Testcontainers

### Frontend

- React
- TypeScript
- Vite
- a small API client layer
- Vitest
- React Testing Library

### Infrastructure and quality

- Docker Compose
- GitHub Actions
- Spring Boot Actuator
- structured application logs
- formatting and static checks

## 6. Initial architecture

```text
Browser
  |
  v
React + TypeScript frontend
  |
  v
Spring Boot REST API
  |
  +--> Dataset service
  +--> File ingestion service
  +--> Validation service
  +--> Reporting service
  |
  v
PostgreSQL
```

The MVP uses a modular monolith. Business logic is separated by responsibility, but all backend modules run in one deployable application.

## 7. Main backend modules

### Dataset module

Owns datasets and their metadata.

### Ingestion module

Accepts CSV uploads, stores file metadata, parses rows, and creates validation runs.

### Validation module

Executes deterministic validation rules and stores issues.

### Reporting module

Creates summaries and exports validation results.

### Operations module

Provides health information, runtime metrics, and structured logs.

## 8. Initial data model

### Dataset

- `id`
- `name`
- `description`
- `created_at`

### SourceFile

- `id`
- `dataset_id`
- `original_filename`
- `content_type`
- `size_bytes`
- `sha256`
- `uploaded_at`

### ValidationProfile

- `id`
- `dataset_id`
- `name`
- `created_at`

### ValidationRule

- `id`
- `profile_id`
- `field_name`
- `rule_type`
- `parameters_json`
- `severity`
- `enabled`

### ValidationRun

- `id`
- `dataset_id`
- `source_file_id`
- `profile_id`
- `status`
- `total_rows`
- `valid_rows`
- `invalid_rows`
- `issue_count`
- `started_at`
- `finished_at`
- `failure_reason`

### ValidationIssue

- `id`
- `run_id`
- `row_number`
- `field_name`
- `rule_type`
- `severity`
- `message`
- `observed_value`

## 9. Initial REST API

### Datasets

- `POST /api/datasets`
- `GET /api/datasets`
- `GET /api/datasets/{datasetId}`

### Validation profiles

- `POST /api/datasets/{datasetId}/profiles`
- `GET /api/datasets/{datasetId}/profiles`
- `POST /api/profiles/{profileId}/rules`

### Files and runs

- `POST /api/datasets/{datasetId}/files`
- `POST /api/files/{fileId}/validation-runs`
- `GET /api/validation-runs`
- `GET /api/validation-runs/{runId}`
- `GET /api/validation-runs/{runId}/issues`
- `GET /api/validation-runs/{runId}/report?format=json`
- `GET /api/validation-runs/{runId}/report?format=csv`

### Operations

- `GET /actuator/health`

## 10. MVP acceptance criteria

The MVP is complete when all of the following are true:

1. The repository can be started locally with documented commands.
2. PostgreSQL schema creation is handled through migrations.
3. A dataset and validation profile can be created through the API.
4. A CSV file can be uploaded.
5. A validation run processes the uploaded file.
6. All five initial rule types work.
7. Validation issues are persisted and queryable.
8. Failed processing produces a useful failure state instead of an unhandled crash.
9. The frontend shows datasets, runs, summaries, and issues.
10. A report can be exported as JSON and CSV.
11. Backend integration tests use a real PostgreSQL test container.
12. The CI pipeline runs backend and frontend checks.
13. Health status and structured logs are available.
14. The README explains setup, architecture, tests, and known limitations.

## 11. Development milestones

### Milestone 1 — Foundation

- repository structure
- Spring Boot application
- React/TypeScript application
- Docker Compose with PostgreSQL
- CI skeleton
- health endpoint

### Milestone 2 — Persistence

- database migrations
- dataset entities and endpoints
- validation profile and rule entities
- repository integration tests

### Milestone 3 — Ingestion

- CSV upload
- file metadata
- checksum
- parser error handling
- validation-run lifecycle

### Milestone 4 — Validation engine

- deterministic rule interface
- five rule implementations
- issue persistence
- run summary calculation
- unit and integration tests

### Milestone 5 — Frontend

- dataset list
- run list
- run detail page
- issue filtering
- error and loading states

### Milestone 6 — Reporting and operations

- JSON and CSV report export
- structured logs
- runtime metrics
- documentation
- final MVP review

## 12. Later extensions

Only after the MVP:

- background worker or queue
- configurable schema mapping
- rule templates
- role-based access
- Prometheus and Grafana
- cloud deployment
- data profiling statistics
- anomaly detection
- AI-assisted explanations of validation issues
- human-reviewed repair suggestions

AI features remain advisory. They must not alter data automatically.
