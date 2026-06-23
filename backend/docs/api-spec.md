# FinDoc Analyzer — Backend API Specification

> Generated from the vertical-sliced hexagonal backend (`features/` + `infra/`).
> Use this document for UI/UX design and frontend integration.

---

## 1. Overview

| Property | Value |
|---|---|
| **Base URL (local)** | `http://localhost:8080/api/v1` |
| **Base URL (Docker)** | `http://backend:8080/api/v1` |
| **API prefix** | `/api/v1` |
| **Content-Type** | `application/json` for all backend endpoints |
| **Auth scheme** | Bearer JWT (`Authorization: Bearer <accessToken>`) |
| **Date/time format** | ISO-8601 UTC strings (e.g. `"2026-06-19T14:30:00Z"`) |
| **IDs** | UUID v4 strings |

### 1.1 Authentication flow

1. **First boot:** call `POST /onboarding` once to create the initial super admin (only works when the database has zero users).
2. **Login:** call `POST /auth/login` with username/password → receive `accessToken`.
3. **Authenticated requests:** send `Authorization: Bearer <accessToken>` on every protected endpoint.
4. **JWT claims** (decoded client-side for routing/UI only; server is source of truth):
   - `sub` — username
   - `userId` — UUID
   - `role` — `SUPER_ADMIN` | `ADMIN` | `MEMBER`
   - `teamId` — UUID or absent/`null` for `SUPER_ADMIN`

### 1.2 Roles & tenancy

| Role | Team | Vault & Chat | User management | Team management |
|---|---|---|---|---|
| `SUPER_ADMIN` | None (teamless) | **403** — not a team member | Full (all users) | Full |
| `ADMIN` | Own team | Full (own team vault) | Own team only | **403** |
| `MEMBER` | Own team | Full (own team vault) | **403** | **403** |

Documents and chat are **team-scoped**. Callers without a `teamId` (super admins) receive **403** with message `"This account is not a member of a team"`.

### 1.3 Standard error response

All application errors return:

```json
{
  "error": "Human-readable message"
}
```

| HTTP Status | When |
|---|---|
| `400 Bad Request` | Validation failure (`@Valid`), illegal argument |
| `401 Unauthorized` | Missing/invalid JWT, wrong login credentials |
| `403 Forbidden` | Insufficient role, team-scoped denial, business-rule denial |
| `404 Not Found` | User, team, or document not found |
| `409 Conflict` | Duplicate username/team name, team not empty, system already initialized |
| `502 Bad Gateway` | AI analysis pipeline failure |

Validation errors return the **first** field error message in `error`.

Spring Security returns **401** for unauthenticated requests and **403** for `@PreAuthorize` failures (no JSON body guaranteed).

---

## 2. Shared types

### 2.1 Enums

#### `UserRole`

```
SUPER_ADMIN | ADMIN | MEMBER
```

#### `DocumentStatus`

```
PENDING     — uploaded / awaiting or queued for analysis
PROCESSING  — analysis in progress
COMPLETED   — analysis finished; aiSummary available
FAILED      — analysis failed; may be re-triggered
```

### 2.2 Reusable schemas

#### `UserView`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `string (uuid)` | yes | |
| `username` | `string` | yes | |
| `role` | `UserRole` | yes | |
| `teamId` | `string (uuid) \| null` | yes | `null` for super admin |

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "username": "jane.admin",
  "role": "ADMIN",
  "teamId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

#### `TeamView`

| Field | Type | Required |
|---|---|---|
| `id` | `string (uuid)` | yes |
| `name` | `string` | yes |
| `createdAt` | `string (ISO-8601)` | yes |

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "name": "Acme Finance",
  "createdAt": "2026-06-19T10:00:00Z"
}
```

#### `DocumentSummaryResponse`

| Field | Type | Required |
|---|---|---|
| `id` | `string (uuid)` | yes |
| `fileName` | `string` | yes |
| `fileSize` | `integer (int64)` | yes | bytes |
| `contentType` | `string` | yes | MIME type |
| `uploadedAt` | `string (ISO-8601)` | yes |
| `status` | `DocumentStatus` | yes |

#### `DocumentDetailResponse`

Extends summary with:

| Field | Type | Required | Notes |
|---|---|---|---|
| `aiSummary` | `string \| null` | yes | populated when `status` is `COMPLETED` |

#### `UploadResult`

| Field | Type | Required | Notes |
|---|---|---|---|
| `documentId` | `string (uuid)` | yes | |
| `fileName` | `string` | yes | |
| `status` | `string` | yes | enum name, e.g. `"PENDING"` |
| `uploadUrl` | `string (url)` | yes | presigned S3 PUT URL, expires in **5 minutes** |

---

## 3. Endpoints

### 3.1 Identity — Onboarding

Bootstrap the system with the first (and only creatable-via-API) super admin.

#### `POST /onboarding`

| | |
|---|---|
| **Auth** | None (public) |
| **Success** | `201 Created` |

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `username` | `string` | yes | not blank |
| `password` | `string` | yes | not blank, min 8 characters |

```json
{
  "username": "root.admin",
  "password": "securepass123"
}
```

**Response body** — `UserView`

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "username": "root.admin",
  "role": "SUPER_ADMIN",
  "teamId": null
}
```

**Error cases**

| Status | Condition |
|---|---|
| `409` | Any user already exists — `"The system has already been initialized; onboarding is disabled"` |
| `400` | Validation failure |

---

### 3.2 Identity — Authentication

#### `POST /auth/login`

| | |
|---|---|
| **Auth** | None (public) |
| **Success** | `200 OK` |

**Request body**

| Field | Type | Required |
|---|---|---|
| `username` | `string` | yes |
| `password` | `string` | yes |

```json
{
  "username": "jane.admin",
  "password": "herpassword"
}
```

**Response body**

| Field | Type | Required |
|---|---|---|
| `accessToken` | `string` | yes | JWT |

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Error cases**

| Status | Condition |
|---|---|
| `401` | `"Invalid username or password"` |

---

### 3.3 Identity — Teams

> **Requires role:** `SUPER_ADMIN` only (`@PreAuthorize("hasRole('SUPER_ADMIN')")`)

#### `POST /teams`

Create a team.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `201 Created` |

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `name` | `string` | yes | not blank |

```json
{
  "name": "Acme Finance"
}
```

**Response body** — `TeamView`

**Error cases**

| Status | Condition |
|---|---|
| `409` | Duplicate team name |
| `403` | Caller is not super admin |

---

#### `GET /teams`

List all teams.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `200 OK` |

**Request body** — none

**Response body** — `TeamView[]`

```json
[
  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "name": "Acme Finance",
    "createdAt": "2026-06-19T10:00:00Z"
  }
]
```

---

#### `PUT /teams/{id}`

Rename a team.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — team UUID |
| **Success** | `200 OK` |

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `name` | `string` | yes | not blank |

```json
{
  "name": "Acme Corp Finance"
}
```

**Response body** — `TeamView`

**Error cases**

| Status | Condition |
|---|---|
| `404` | Team not found |
| `409` | Duplicate team name |

---

#### `DELETE /teams/{id}`

Delete an empty team.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — team UUID |
| **Success** | `204 No Content` |

**Request body** — none  
**Response body** — empty

**Error cases**

| Status | Condition |
|---|---|
| `404` | Team not found |
| `409` | Team still has members — `"Team still has members; remove or reassign them before deleting"` |

---

### 3.4 Identity — User management

> **Requires role:** `SUPER_ADMIN` or `ADMIN` (`@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")`)

#### `POST /users`

Create a user.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `201 Created` |

**Request body**

| Field | Type | Required | Validation | Notes |
|---|---|---|---|---|
| `username` | `string` | yes | not blank | |
| `password` | `string` | yes | not blank, min 8 chars | |
| `role` | `UserRole` | conditional | | **Required for SUPER_ADMIN.** Ignored when caller is ADMIN (always creates `MEMBER`). Cannot be `SUPER_ADMIN`. |
| `teamId` | `string (uuid)` | conditional | | **Required for SUPER_ADMIN.** Ignored when caller is ADMIN (uses admin's team). |

```json
{
  "username": "bob.member",
  "password": "memberpass1",
  "role": "MEMBER",
  "teamId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

**ADMIN caller** — only `username` and `password` matter:

```json
{
  "username": "bob.member",
  "password": "memberpass1"
}
```

**Response body** — `UserView`

**Error cases**

| Status | Condition |
|---|---|
| `400` | Missing `role` or `teamId` (super admin caller) |
| `403` | Attempt to create another super admin |
| `404` | Team not found (super admin caller) |
| `409` | Username already taken |

---

#### `GET /users`

List users visible to the caller.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `200 OK` |

**Request body** — none

**Response body** — `UserView[]`

- **SUPER_ADMIN** → all users system-wide
- **ADMIN** → users in own team only

---

#### `PATCH /users/{id}/role`

Change a user's role between `ADMIN` and `MEMBER`.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — user UUID |
| **Success** | `200 OK` |

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `role` | `UserRole` | yes | not null; cannot be `SUPER_ADMIN` |

```json
{
  "role": "ADMIN"
}
```

**Response body** — `UserView`

**Business rules**

| Caller | Allowed changes |
|---|---|
| `SUPER_ADMIN` | Any `ADMIN` ↔ `MEMBER` transition |
| `ADMIN` | Promote own-team `MEMBER` → `ADMIN` only (no demotion) |

**Error cases**

| Status | Condition |
|---|---|
| `403` | Assign super admin role, change super admin's role, cross-team access, admin attempting demotion |
| `404` | User not found |

---

#### `DELETE /users/{id}`

Delete a user.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — user UUID |
| **Success** | `204 No Content` |

**Request body** — none  
**Response body** — empty

**Business rules**

| Caller | Allowed |
|---|---|
| `SUPER_ADMIN` | Delete anyone except self; cannot delete last super admin |
| `ADMIN` | Delete `MEMBER` in own team only; cannot delete admins or self |

**Error cases**

| Status | Condition |
|---|---|
| `403` | Self-delete, delete last super admin, cross-team, admin deleting another admin |
| `404` | User not found |

---

### 3.5 Vault — Documents

> **Requires:** authenticated user **with a team** (`ADMIN` or `MEMBER`).

#### `GET /documents`

List all documents in the caller's team vault.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `200 OK` |

**Request body** — none

**Response body** — `DocumentSummaryResponse[]`

```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "Q4-2025-report.pdf",
    "fileSize": 1048576,
    "contentType": "application/pdf",
    "uploadedAt": "2026-06-19T12:00:00Z",
    "status": "COMPLETED"
  }
]
```

**Error cases**

| Status | Condition |
|---|---|
| `403` | Super admin (no team) or unauthenticated |

---

#### `GET /documents/{id}`

Get document details including AI summary.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — document UUID |
| **Success** | `200 OK` |

**Response body** — `DocumentDetailResponse`

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fileName": "Q4-2025-report.pdf",
  "fileSize": 1048576,
  "contentType": "application/pdf",
  "uploadedAt": "2026-06-19T12:00:00Z",
  "status": "COMPLETED",
  "aiSummary": "The document covers Q4 2025 financial results..."
}
```

**Error cases**

| Status | Condition |
|---|---|
| `404` | Document not found or not in caller's team |
| `403` | Super admin (no team) |

---

#### `POST /documents`

Initiate a document upload (step 1 of upload flow).

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `201 Created` |

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `fileName` | `string` | yes | not blank |
| `size` | `integer (int64)` | yes | not null, positive |
| `type` | `string` | yes | not blank — MIME type (e.g. `"application/pdf"`) |

```json
{
  "fileName": "Q4-2025-report.pdf",
  "size": 1048576,
  "type": "application/pdf"
}
```

**Response body** — `UploadResult`

```json
{
  "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fileName": "Q4-2025-report.pdf",
  "status": "PENDING",
  "uploadUrl": "https://s3.amazonaws.com/bucket/files/a1b2c3d4-...?X-Amz-..."
}
```

**Error cases**

| Status | Condition |
|---|---|
| `400` | Validation failure |
| `403` | Super admin (no team) |

---

#### `GET /documents/{id}/view`

Get a presigned URL to view/download the raw file.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — document UUID |
| **Success** | `200 OK` |

**Response body**

```json
{
  "viewUrl": "https://s3.amazonaws.com/bucket/files/a1b2c3d4-...?X-Amz-..."
}
```

Presigned GET URL expires in **15 minutes**.

**Error cases**

| Status | Condition |
|---|---|
| `404` | Document not found or not in caller's team |

---

#### `POST /documents/{id}/analyze`

Request (or re-request) AI analysis for a document.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — document UUID |
| **Success** | `200 OK` |

**Request body** — none

**Response body** — `DocumentDetailResponse`

**Behavior**

- Allowed when `status` is `PENDING` or `FAILED`.
- If `status` is `PROCESSING` or `COMPLETED`, returns current document **without re-queuing** (idempotent no-op).
- In production, analysis may also start automatically via S3 upload events; this endpoint is the explicit trigger (required in local/dev when S3 events are unavailable).

**Error cases**

| Status | Condition |
|---|---|
| `404` | Document not found |
| `502` | AI pipeline failure (during processing, not at request time) |

---

#### `DELETE /documents/{id}`

Delete a document from the vault (and object storage).

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Path params** | `id` — document UUID |
| **Success** | `204 No Content` |

**Request body** — none  
**Response body** — empty

**Error cases**

| Status | Condition |
|---|---|
| `404` | Document not found or not in caller's team |

---

### 3.6 Chat — AI Q&A

> **Requires:** authenticated user **with a team** (`ADMIN` or `MEMBER`).

#### `POST /chat`

Ask a financial question grounded in the team's indexed documents.

| | |
|---|---|
| **Auth** | Bearer JWT |
| **Success** | `200 OK` |

**Request body**

| Field | Type | Required | Validation |
|---|---|---|---|
| `question` | `string` | yes | not blank |

```json
{
  "question": "What was the net revenue in Q4 2025?"
}
```

**Response body**

| Field | Type | Required |
|---|---|---|
| `answer` | `string` | yes |

```json
{
  "answer": "Based on the uploaded documents, net revenue in Q4 2025 was $12.4M..."
}
```

**Informational answers** (still `200 OK`, not errors):

| Scenario | Example `answer` |
|---|---|
| No indexed documents | `"No relevant financial data found in your document vault."` |
| Documents found but no match | `"I found some documents, but they don't contain a specific answer to your question."` |

**Error cases**

| Status | Condition |
|---|---|
| `400` | Empty question |
| `403` | Super admin (no team) |

---

## 4. Client-side flows

### 4.1 Document upload (two-step)

```
┌──────────┐   POST /documents          ┌─────────┐
│ Frontend │ ─────────────────────────► │ Backend │
│          │ ◄───────────────────────── │         │
└──────────┘   { documentId, uploadUrl } └─────────┘
      │
      │  PUT uploadUrl  (direct to S3, not backend)
      │  Headers: Content-Type: <same as request.type>
      ▼
┌──────────┐
│    S3    │
└──────────┘
      │
      │  (optional) POST /documents/{id}/analyze
      ▼
┌─────────┐   poll GET /documents/{id}   ┌──────────┐
│ Frontend│ ◄──────────────────────────► │ Backend  │
└─────────┘   until status ≠ PROCESSING  └──────────┘
```

1. `POST /documents` with file metadata → receive `uploadUrl` and `documentId`.
2. `PUT` the raw file bytes to `uploadUrl` with `Content-Type` matching the `type` field sent in step 1.
3. Call `POST /documents/{id}/analyze` if analysis does not start automatically.
4. Poll `GET /documents/{id}` until `status` is `COMPLETED` or `FAILED`.

### 4.2 Suggested frontend routes → API mapping

| Screen | Primary endpoints | Roles |
|---|---|---|
| `/onboarding` | `POST /onboarding` | public (once) |
| `/login` | `POST /auth/login` | public |
| `/dashboard` (vault) | `GET /documents`, `POST /documents`, `DELETE /documents/{id}` | ADMIN, MEMBER |
| Document detail / insights | `GET /documents/{id}`, `GET /documents/{id}/view`, `POST /documents/{id}/analyze` | ADMIN, MEMBER |
| `/chat` | `POST /chat` | ADMIN, MEMBER |
| Admin — teams | `GET/POST /teams`, `PUT/DELETE /teams/{id}` | SUPER_ADMIN |
| Admin — users | `GET/POST /users`, `PATCH/DELETE /users/{id}/role` | SUPER_ADMIN, ADMIN |

---

## 5. Endpoint index

| Method | Path | Auth | Roles | Success |
|---|---|---|---|---|
| `POST` | `/onboarding` | — | public | 201 |
| `POST` | `/auth/login` | — | public | 200 |
| `POST` | `/teams` | JWT | SUPER_ADMIN | 201 |
| `GET` | `/teams` | JWT | SUPER_ADMIN | 200 |
| `PUT` | `/teams/{id}` | JWT | SUPER_ADMIN | 200 |
| `DELETE` | `/teams/{id}` | JWT | SUPER_ADMIN | 204 |
| `POST` | `/users` | JWT | SUPER_ADMIN, ADMIN | 201 |
| `GET` | `/users` | JWT | SUPER_ADMIN, ADMIN | 200 |
| `PATCH` | `/users/{id}/role` | JWT | SUPER_ADMIN, ADMIN | 200 |
| `DELETE` | `/users/{id}` | JWT | SUPER_ADMIN, ADMIN | 204 |
| `GET` | `/documents` | JWT | ADMIN, MEMBER | 200 |
| `GET` | `/documents/{id}` | JWT | ADMIN, MEMBER | 200 |
| `POST` | `/documents` | JWT | ADMIN, MEMBER | 201 |
| `GET` | `/documents/{id}/view` | JWT | ADMIN, MEMBER | 200 |
| `POST` | `/documents/{id}/analyze` | JWT | ADMIN, MEMBER | 200 |
| `DELETE` | `/documents/{id}` | JWT | ADMIN, MEMBER | 204 |
| `POST` | `/chat` | JWT | ADMIN, MEMBER | 200 |

---

## 6. Source references

Controllers scanned:

- `LoginController` — `/api/v1/auth`
- `OnboardingController` — `/api/v1/onboarding`
- `TeamController` — `/api/v1/teams`
- `UserManagementController` — `/api/v1/users`
- `DocumentController` — `/api/v1/documents`
- `ChatController` — `/api/v1/chat`

Security: `SecurityConfig` (public: `/auth/**`, `POST /onboarding`; all else authenticated).

Errors: `GlobalExceptionHandler`.
