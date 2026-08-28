# FinDoc Analyzer Backend

Spring Boot API for identity, team-scoped document management, SEC filing ingestion, asynchronous analysis, retrieval, and citation-grounded chat.

## Responsibilities

- Bootstrap the first super administrator and issue JWT access tokens.
- Manage teams, administrators, and members with role-aware authorization.
- Create presigned S3 upload and view URLs for team documents.
- Search SEC companies, list supported filings, and import them through the EDGAR sidecar.
- Parse uploaded documents with Docling and ingest EDGAR narrative sections without fabricating page provenance.
- Persist metadata, amendment relationships, durable analysis requests, and MiniLM embeddings in PostgreSQL/pgvector.
- Retrieve progressively with the original question, query rewriting, and HyDE fallback attempts.
- Generate Gemini answers with request-local numbered citations validated against retrieved sources.

## Stack

- Java 21
- Spring Boot 4.0.5 with Spring MVC, Security, Data JPA, Flyway, and Spring Cloud AWS
- LangChain4j with Google Gemini and the local `all-MiniLM-L6-v2` embedding model
- PostgreSQL with pgvector
- AWS S3 and SQS
- Docling Serve and the internal EDGAR FastAPI service
- JUnit 5, AssertJ, Maven Failsafe, and Testcontainers

The code is feature-sliced under `features/identity`, `features/vault`, and `features/chat`. Each feature keeps application use cases and ports between its web, persistence, storage, messaging, parsing, vector, and LLM adapters. Shared security, configuration, and error mapping live under `infra`.

## Prerequisites

- Java 21+
- Docker for PostgreSQL integration tests and the local dependency stack
- PostgreSQL with the `vector` extension
- AWS S3 and SQS resources
- Running Docling and EDGAR services for real ingestion
- A Gemini API key for chat and production evaluation

From the repository root, `make dev` starts PostgreSQL, Docling, and EDGAR on their local ports. AWS is not emulated by the Compose development overlay.

## Configuration

The application optionally imports `.env` from its working directory. For source development:

```bash
cd backend
cp ../.env.example .env
```

Required settings:

| Variable                                                     | Purpose                                                                                    |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| `POSTGRES_DB`                                                | Database name used by the default JDBC URL                                                 |
| `POSTGRES_USER` / `POSTGRES_PASSWORD`                        | Database credentials                                                                       |
| `SPRING_DATASOURCE_URL`                                      | Optional full JDBC override; defaults to `jdbc:postgresql://localhost:5432/${POSTGRES_DB}` |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION` | Credentials and region for S3 and SQS                                                      |
| `AWS_S3_BUCKET_NAME`                                         | Document object bucket                                                                     |
| `AWS_SQS_QUEUE_NAME`                                         | Queue consumed and published by the analysis pipeline                                      |
| `JWT_SECRET`                                                 | HMAC secret for access tokens; use a strong value                                          |
| `GEMINI_API_KEY`                                             | Google Gemini API key                                                                      |

Service endpoints and common tuning:

| Variable                    | Default                 | Purpose                                       |
| --------------------------- | ----------------------- | --------------------------------------------- |
| `DOCLING_URL`               | `http://localhost:5001` | Docling Serve base URL                        |
| `DOCLING_TIMEOUT`           | `PT5M`                  | Document conversion timeout                   |
| `EDGAR_SERVICE_URL`         | `http://localhost:8100` | EDGAR sidecar base URL                        |
| `GEMINI_TEMPERATURE`        | `0.0`                   | Answer model temperature                      |
| `GEMINI_SEED`               | `42`                    | Answer model seed                             |
| `RETRIEVAL_MAX_SECTIONS`    | `6`                     | Maximum final context sections                |
| `RETRIEVAL_MIN_SCORE`       | `0.60`                  | Minimum accepted similarity score             |
| `RETRIEVAL_TRACE_POOL_SIZE` | `15`                    | Candidate pool retained for retrieval tracing |

Analysis outbox timing and batch settings are also environment-configurable through the `ANALYSIS_OUTBOX_*` variables documented in `src/main/resources/application.yml`.

## Run locally

Start dependencies from the repository root:

```bash
make dev
```

Start the API:

```bash
cd backend
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080` and uses the `/api/v1` prefix.

For the containerized full stack, use `make prod` from the repository root. Compose supplies internal PostgreSQL, Docling, and EDGAR URLs and activates the `prod` profile.

## Profiles

| Profile | Behavior                                                                    |
| ------- | --------------------------------------------------------------------------- |
| default | Normal application behavior and local service defaults                      |
| `prod`  | Writes logs to `LOG_FILE`, defaulting to `logs/findoc-analyzer.log`         |
| `demo`  | Enforces 15 documents, 10 users, 3 teams, and 5 MB per upload               |
| `eval`  | Uses recorded filing fixtures and enables the evaluation-only chat contract |

The demo profile limits resources but does not seed users.

## API surface

Except for authentication and one-time onboarding, endpoints require `Authorization: Bearer <token>`.

| Method and path                                   | Access                             | Purpose                                                         |
| ------------------------------------------------- | ---------------------------------- | --------------------------------------------------------------- |
| `GET /api/v1/onboarding/status`                   | Public                             | Report whether initial onboarding is available                  |
| `POST /api/v1/onboarding`                         | Public until the first user exists | Create the sole `SUPER_ADMIN`                                   |
| `POST /api/v1/auth/login`                         | Public                             | Authenticate and issue a JWT                                    |
| `GET`, `POST`, `PUT`, `DELETE /api/v1/teams...`   | `SUPER_ADMIN`                      | Manage teams                                                    |
| `GET`, `POST`, `PATCH`, `DELETE /api/v1/users...` | `SUPER_ADMIN` or `ADMIN`           | Manage users within service-enforced scope                      |
| `GET`, `POST`, `DELETE /api/v1/documents...`      | `ADMIN` or `MEMBER`                | List, inspect, upload, view, analyze, and delete team documents |
| `GET /api/v1/edgar/companies`                     | `ADMIN` or `MEMBER`                | Search SEC filers                                               |
| `GET /api/v1/edgar/companies/{id}/filings`        | `ADMIN` or `MEMBER`                | List one supported filing form                                  |
| `POST /api/v1/edgar/filings/import`               | `ADMIN` or `MEMBER`                | Import a `10-K`, `10-K/A`, `10-Q`, or `10-Q/A`                  |
| `POST /api/v1/chat`                               | Authenticated team member          | Ask a question and receive numbered citation metadata           |
| `GET /api/v1/admin/analysis-outbox`               | `SUPER_ADMIN`                      | Inspect outbox health and stuck work                            |
| `POST /api/v1/eval/chat`                          | `eval` profile only                | Exercise the production answer path with evaluation tracing     |

The controller classes under `src/main/java/.../adapter/in/web` are the source of truth for payloads and validation.

## Ingestion and retrieval behavior

Uploaded documents use a two-step flow: the API creates metadata and a presigned S3 upload URL, then an S3 object-created event reaches the configured SQS queue. The listener downloads the object, sends it to Docling, and atomically replaces its vector rows.

EDGAR imports persist filing metadata and a durable outbox row in one transaction. A scheduled outbox publisher sends due requests to SQS with leases, exponential retry delay, duplicate suppression, stuck-work reporting, and cleanup. The worker calls the EDGAR sidecar for normalized sections. A valid filing with no searchable narrative is completed with `searchable=false` and no embeddings.

Amendments retain their own filing metadata and link to the original accession when resolvable. Retrieval groups a filing family by original accession and section, then favors the latest effective text so amendment evidence takes precedence without discarding unresolved valid originals.

Chat returns human-readable citation fields—accession, form, filing date, section, title, page, and excerpt. Internal embedding identifiers do not cross the public API.

## Tests

Fast unit and Spring slice tests:

```bash
./mvnw test
```

Full default verification, including `*IT` tests through Maven Failsafe:

```bash
./mvnw verify
```

Integration tests use PostgreSQL/Testcontainers where persistence or vector behavior matters. Docker must be available.

Run the real Docling test after starting Docling:

```bash
./mvnw -Ddocling.e2e=true -Dtest=DoclingDocumentParserAdapterE2ETest test
```

Run the opt-in evaluation integration tests through the repository Makefile:

```bash
make eval-ingest
make eval-retrieval
make eval-production
```

`make eval-production` loads `backend/.env`, requires `GEMINI_API_KEY`, and makes a real model request. See [../evals/README.md](../evals/README.md) for the complete evaluation workflow.

## Layout

```text
src/main/java/com/danycb/findocAnalyzer/
  features/
    identity/   onboarding, login, teams, users
    vault/      documents, EDGAR import, analysis, storage, vectors
    chat/       retrieval, prompts, Gemini, citation contracts
  infra/        shared configuration, security, and error handling
src/main/resources/
  db/migration/ Flyway schema migrations
  prompts/      production retrieval and answer prompts
src/test/java/  unit, slice, integration, E2E, and eval tests
```
