# Data Quality Platform

The Data Quality Platform is a learning and software-engineering project for building a full-stack data validation application in small, verifiable milestones.

## Current status

Milestone 1 provides the project foundation. The first Milestone 2 implementation step adds the PostgreSQL persistence foundation:

- a Java 21 and Spring Boot backend
- a React and TypeScript frontend
- a local PostgreSQL service through Docker Compose
- environment-backed backend datasource configuration
- Spring Data JPA, Bean Validation, and Flyway infrastructure
- a PostgreSQL Testcontainers integration test
- backend and frontend tests and formatting checks
- a GitHub Actions workflow for repository checks

The backend currently exposes only the Actuator health endpoint. It connects to PostgreSQL at startup, and the health aggregation includes database availability. The frontend remains a static application shell. Flyway is enabled, but no application migrations or business tables exist yet.

Dataset management, dataset entities, repositories, CSV uploads, validation rules, reports, authentication, and AI features are not implemented yet.

## Repository layout

```text
backend/                 Spring Boot application, persistence foundation, and Maven Wrapper
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

- `test` starts an isolated PostgreSQL Testcontainer and runs the Spring Boot integration test.
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

- Milestone 2 remaining work: database migrations, datasets, validation profiles, and rules
- Milestone 3: CSV ingestion and validation-run lifecycle
- Milestone 4: deterministic validation rules and issue persistence
- Milestone 5: dataset, run, summary, and issue screens
- Milestone 6: report export, structured logs, runtime metrics, and final documentation

The detailed product scope and milestone definitions are maintained in `PROJECT_BRIEF.md`.
