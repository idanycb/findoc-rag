# FinDoc Analyzer - Backend API Specification v2

> Updated from the current Spring controllers and the Postman end-to-end collection.
> Use this for frontend integration and QA flows.

---

## 1. Overview

| Property | Value |
|---|---|
| Base URL (local) | `http://localhost:8080/api/v1` |
| API prefix | `/api/v1` |
| Backend JSON content type | `application/json` |
| Auth scheme | Bearer JWT: `Authorization: Bearer <accessToken>` |
| Date/time format | ISO-8601 timestamps, for example `"2026-06-21T17:48:01Z"` |
| IDs | UUID strings |

### 1.1 Authentication Flow

1. Call `POST /onboarding` once on a fresh database to create the initial `SUPER_ADMIN`.
2. Call `POST /auth/login` with username and password to receive an `accessToken`.
3. Send `Authorization: Bearer <accessToken>` on every protected backend request.
4. Decode JWTs client-side only for UI routing; the backend remains the source of truth.

Typical JWT claims:

| Claim | Type | Notes |
|---|---|---|
| `sub` | `string` | Username |
| `userId` | `string (uuid)` | User ID |
| `role` | `SUPER_ADMIN \| ADMIN \| MEMBER` | User role |
| `teamId` | `string (uuid)` or absent | Present for `ADMIN` and `MEMBER`; absent for `SUPER_ADMIN` |
| `iat` | `integer` | Issued-at timestamp |
| `exp` | `integer` | Expiry timestamp |

### 1.2 Roles And Tenancy

| Role | Team | Teams API | Users API | Documents API | Chat API |
|---|---|---|---|---|---|
| `SUPER_ADMIN` | No team | Full access | Full access | Not allowed | Not allowed |
| `ADMIN` | Own team | Not allowed | Own team management | Own team vault | Own team docs |
| `MEMBER` | Own team | Not allowed | Not allowed | Own team vault | Own team docs |

Documents, analysis, and chat are team-scoped. Document endpoints are protected by `ADMIN` or `MEMBER` role checks. Chat is authenticated and explicitly rejects callers without a `teamId`.

### 1.3 Standard Error Response

Application errors return:

```json
{
  "error": "Human-readable message"
}
```

| HTTP status | When |
|---|---|
| `400 Bad Request` | Validation failure or `IllegalArgumentException` |
| `401 Unauthorized` | Invalid credentials or invalid access token |
| `403 Forbidden` | Role denial, team-scope denial, or forbidden business rule |
| `404 Not Found` | User, team, or document not found |
| `409 Conflict` | Onboarding disabled, duplicate username/team name, or team still has members |
| `502 Bad Gateway` | AI chat/analysis integration failure |

Validation returns the first field error message in `error`.

---

## 2. Shared Types

### 2.1 Enums

```text
UserRole = SUPER_ADMIN | ADMIN | MEMBER
DocumentStatus = PENDING | PROCESSING | COMPLETED | FAILED
```

Document status meanings:

| Status | Meaning |
|---|---|
| `PENDING` | Upload initiated or queued for analysis |
| `PROCESSING` | Analysis worker is processing the document |
| `COMPLETED` | Analysis and vector indexing completed |
| `FAILED` | Analysis failed; it can be requested again |

### 2.2 Reusable Schemas

#### `UserView`

| Field | Type | Notes |
|---|---|---|
| `id` | `string (uuid)` | User ID |
| `username` | `string` | Login username |
| `role` | `UserRole` | User role |
| `teamId` | `string (uuid) \| null` | `null` for `SUPER_ADMIN` |

```json
{
  "id": "03f32e0f-ea94-4516-805b-036129ecd5c1",
  "username": "super_admin",
  "role": "SUPER_ADMIN",
  "teamId": null
}
```

#### `TeamView`

| Field | Type | Notes |
|---|---|---|
| `id` | `string (uuid)` | Team ID |
| `name` | `string` | Team name |
| `createdAt` | `string (ISO-8601)` | Creation timestamp |

```json
{
  "id": "9c1af964-e889-40f7-b212-772ddb4c5ae4",
  "name": "demo",
  "createdAt": "2026-06-21T17:40:00Z"
}
```

#### `DocumentSummaryResponse`

| Field | Type | Notes |
|---|---|---|
| `id` | `string (uuid)` | Document ID |
| `fileName` | `string` | Original file name |
| `fileSize` | `integer (int64)` | Size in bytes |
| `contentType` | `string` | MIME type |
| `uploadedAt` | `string (ISO-8601)` | Upload-initiation timestamp |
| `status` | `DocumentStatus` | Current analysis status |

#### `DocumentDetailResponse`

`DocumentDetailResponse` contains all `DocumentSummaryResponse` fields plus:

| Field | Type | Notes |
|---|---|---|
| `lastAnalyzedAt` | `string (ISO-8601) \| null` | Set when analysis completes; `null` before completion or after failed analysis with no prior success |

```json
{
  "id": "1a46382e-702a-4980-a81c-3d50b22c6750",
  "fileName": "Python Mastery.pdf",
  "fileSize": 518680,
  "contentType": "application/pdf",
  "uploadedAt": "2026-06-21T17:48:01Z",
  "status": "COMPLETED",
  "lastAnalyzedAt": "2026-06-21T17:50:12Z"
}
```

Note: v2 document details do not return an `aiSummary` field. Chat answers are produced through `POST /chat`.

#### `UploadResult`

| Field | Type | Notes |
|---|---|---|
| `documentId` | `string (uuid)` | New document ID |
| `fileName` | `string` | Original file name |
| `status` | `string` | Initially `"PENDING"` |
| `uploadUrl` | `string (url)` | Presigned S3 `PUT` URL |

```json
{
  "documentId": "1a46382e-702a-4980-a81c-3d50b22c6750",
  "fileName": "Python Mastery.pdf",
  "status": "PENDING",
  "uploadUrl": "https://findoc-analyzer.s3.us-east-2.amazonaws.com/files/1a46382e-702a-4980-a81c-3d50b22c6750?X-Amz-..."
}
```

---

## 3. Endpoints

### 3.1 Onboarding

#### `POST /onboarding`

Creates the initial super admin. Public, but allowed only while the database has no users.

| | |
|---|---|
| Auth | None |
| Success | `201 Created` |

Request body:

| Field | Type | Required | Validation |
|---|---|---|---|
| `username` | `string` | yes | Not blank |
| `password` | `string` | yes | Not blank, min 8 chars |

```json
{
  "username": "super_admin",
  "password": "dummy_pass"
}
```

Response body: `UserView`

Error cases:

| Status | Condition |
|---|---|
| `400` | Validation failure |
| `409` | Any user already exists: `"System already initialized; onboarding is disabled"` |

---

### 3.2 Authentication

#### `POST /auth/login`

Authenticates a user and returns a JWT access token.

| | |
|---|---|
| Auth | None |
| Success | `200 OK` |

Request body:

| Field | Type | Required |
|---|---|---|
| `username` | `string` | yes |
| `password` | `string` | yes |

```json
{
  "username": "admin",
  "password": "dummy_pass"
}
```

Response body:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

Error cases:

| Status | Condition |
|---|---|
| `401` | `"Invalid username or password"` |

---

### 3.3 Teams

All team endpoints require `SUPER_ADMIN`.

#### `POST /teams`

Creates a team.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN` |
| Success | `201 Created` |

Request body:

| Field | Type | Required | Validation |
|---|---|---|---|
| `name` | `string` | yes | Not blank |

```json
{
  "name": "demo"
}
```

Response body: `TeamView`

Error cases:

| Status | Condition |
|---|---|
| `400` | `"Team name is required"` |
| `403` | Caller is not `SUPER_ADMIN` |
| `409` | Duplicate name: `"Team: [demo] already exists"` |

#### `GET /teams`

Lists all teams.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN` |
| Success | `200 OK` |

Response body: `TeamView[]`

#### `PUT /teams/{id}`

Renames a team.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN` |
| Path params | `id` - team UUID |
| Success | `200 OK` |

Request body:

```json
{
  "name": "demo-renamed"
}
```

Response body: `TeamView`

Error cases:

| Status | Condition |
|---|---|
| `400` | `"Team name is required"` |
| `403` | Caller is not `SUPER_ADMIN` |
| `404` | `"Team not found: {id}"` |
| `409` | Duplicate name |

#### `DELETE /teams/{id}`

Deletes an empty team.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN` |
| Path params | `id` - team UUID |
| Success | `204 No Content` |

Error cases:

| Status | Condition |
|---|---|
| `403` | Caller is not `SUPER_ADMIN` |
| `404` | `"Team not found: {id}"` |
| `409` | Team has members: `"Team: [{id}] still has members; remove or reassign them before deleting"` |

---

### 3.4 Users

All user-management endpoints require `SUPER_ADMIN` or `ADMIN`.

#### `POST /users`

Creates a user.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN`, `ADMIN` |
| Success | `201 Created` |

Request body for `SUPER_ADMIN` callers:

| Field | Type | Required | Validation / Notes |
|---|---|---|---|
| `username` | `string` | yes | Not blank |
| `password` | `string` | yes | Not blank, min 8 chars |
| `role` | `ADMIN \| MEMBER` | yes | `SUPER_ADMIN` is forbidden |
| `teamId` | `string (uuid)` | yes | Must reference an existing team |

```json
{
  "username": "admin",
  "password": "dummy_pass",
  "role": "ADMIN",
  "teamId": "9c1af964-e889-40f7-b212-772ddb4c5ae4"
}
```

Request body for `ADMIN` callers:

| Field | Type | Required | Validation / Notes |
|---|---|---|---|
| `username` | `string` | yes | Not blank |
| `password` | `string` | yes | Not blank, min 8 chars |
| `role` | `UserRole` | no | Ignored; backend always creates `MEMBER` |
| `teamId` | `string (uuid)` | no | Ignored; backend uses caller's team |

Response body: `UserView`

Error cases:

| Status | Condition |
|---|---|
| `400` | Validation failure, missing `role`, or missing `teamId` for super admin caller |
| `403` | Creating another `SUPER_ADMIN`: `"Cannot create another super admin"` |
| `404` | Team not found |
| `409` | Duplicate username: `"Username already taken: {username}"` |

#### `GET /users`

Lists users visible to the caller.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN`, `ADMIN` |
| Success | `200 OK` |

Response body: `UserView[]`

Visibility:

| Caller | Result |
|---|---|
| `SUPER_ADMIN` | All users |
| `ADMIN` | Users in caller's team |

#### `PATCH /users/{id}/role`

Changes a user's role between `ADMIN` and `MEMBER`.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN`, `ADMIN` |
| Path params | `id` - user UUID |
| Success | `200 OK` |

Request body:

```json
{
  "role": "ADMIN"
}
```

Response body: `UserView`

Business rules:

| Caller | Allowed changes |
|---|---|
| `SUPER_ADMIN` | Any `ADMIN` <-> `MEMBER` transition |
| `ADMIN` | Own-team `MEMBER` -> `ADMIN` only |

Error cases:

| Status | Condition |
|---|---|
| `400` | `"Role is required"` |
| `403` | Assigning `SUPER_ADMIN`, changing a super admin, cross-team access, or admin demotion |
| `404` | `"User not found: {id}"` |

#### `DELETE /users/{id}`

Deletes a user.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `SUPER_ADMIN`, `ADMIN` |
| Path params | `id` - user UUID |
| Success | `204 No Content` |

Business rules:

| Caller | Allowed deletes |
|---|---|
| `SUPER_ADMIN` | Any user except self |
| `ADMIN` | Own-team `MEMBER` users only, except self |

Error cases:

| Status | Condition |
|---|---|
| `403` | Self-delete, cross-team delete, or admin deleting another admin |
| `404` | `"User not found: {id}"` |

---

### 3.5 Documents

All document endpoints require `ADMIN` or `MEMBER`. They operate only on the caller's team vault.

#### `POST /documents`

Initiates a direct-to-S3 upload by creating a document record and returning a presigned upload URL.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `ADMIN`, `MEMBER` |
| Success | `201 Created` |

Request body:

| Field | Type | Required | Validation |
|---|---|---|---|
| `fileName` | `string` | yes | Not blank |
| `size` | `integer (int64)` | yes | Positive |
| `type` | `string` | yes | Not blank MIME type |

```json
{
  "fileName": "Python Mastery.pdf",
  "size": 518680,
  "type": "application/pdf"
}
```

Response body: `UploadResult`

Error cases:

| Status | Condition |
|---|---|
| `400` | `"File name is required"`, `"File size is required"`, `"File size must be positive"`, or `"Content type is required"` |
| `403` | Caller is not `ADMIN` or `MEMBER` |

#### Direct S3 Upload

The file bytes are uploaded to S3, not to the backend.

| | |
|---|---|
| Method | `PUT` |
| URL | `uploadUrl` from `POST /documents` |
| Auth | None |
| Required header | `Content-Type: <same value as request.type>` |
| Body | Raw file bytes |

The Postman collection uses a file body with `Content-Type: application/pdf`.

#### `GET /documents`

Lists all documents in the caller's team vault.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `ADMIN`, `MEMBER` |
| Success | `200 OK` |

Response body: `DocumentSummaryResponse[]`

```json
[
  {
    "id": "1a46382e-702a-4980-a81c-3d50b22c6750",
    "fileName": "Python Mastery.pdf",
    "fileSize": 518680,
    "contentType": "application/pdf",
    "uploadedAt": "2026-06-21T17:48:01Z",
    "status": "COMPLETED"
  }
]
```

#### `GET /documents/{id}`

Gets document details.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `ADMIN`, `MEMBER` |
| Path params | `id` - document UUID |
| Success | `200 OK` |

Response body: `DocumentDetailResponse`

Error cases:

| Status | Condition |
|---|---|
| `403` | Caller is not `ADMIN` or `MEMBER` |
| `404` | Document not found in caller's team |

#### `GET /documents/{id}/view`

Returns a presigned S3 URL to view or download the raw file.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `ADMIN`, `MEMBER` |
| Path params | `id` - document UUID |
| Success | `200 OK` |

Response body:

```json
{
  "viewUrl": "https://findoc-analyzer.s3.us-east-2.amazonaws.com/files/1a46382e-702a-4980-a81c-3d50b22c6750?X-Amz-..."
}
```

Error cases:

| Status | Condition |
|---|---|
| `403` | Caller is not `ADMIN` or `MEMBER` |
| `404` | Document not found in caller's team |

#### `POST /documents/{id}/analyze`

Requests asynchronous document analysis.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `ADMIN`, `MEMBER` |
| Path params | `id` - document UUID |
| Success | `202 Accepted` |
| Response body | Empty |

Behavior:

| Current status | Result |
|---|---|
| `PENDING` | Queues analysis work and returns `202` |
| `FAILED` | Marks pending again, queues reanalysis, and returns `202` |
| `PROCESSING` | No-op; returns `202` without enqueueing |
| `COMPLETED` | No-op; returns `202` without enqueueing |

Analysis runs through the queue/worker path. The worker marks the document `PROCESSING`, downloads the file from storage, parses it, ingests vector chunks, and then marks it `COMPLETED` with `lastAnalyzedAt`. If worker processing fails, the document becomes `FAILED`.

Error cases:

| Status | Condition |
|---|---|
| `403` | Caller is not `ADMIN` or `MEMBER` |
| `404` | Document not found in caller's team |

#### `DELETE /documents/{id}`

Deletes the document record, vector index entries, and object storage file.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | `ADMIN`, `MEMBER` |
| Path params | `id` - document UUID |
| Success | `204 No Content` |

Error cases:

| Status | Condition |
|---|---|
| `403` | Caller is not `ADMIN` or `MEMBER` |
| `404` | Document not found in caller's team |

---

### 3.6 Chat

#### `POST /chat`

Asks a question against the caller's team document vault.

| | |
|---|---|
| Auth | Bearer JWT |
| Roles | Authenticated user with a `teamId`; practically `ADMIN` or `MEMBER` |
| Success | `200 OK` |

Request body:

| Field | Type | Required | Validation |
|---|---|---|---|
| `question` | `string` | yes | Not blank, max 1000 chars |

```json
{
  "question": "What is the info on regex?"
}
```

Response body:

```json
{
  "answer": "Based on the uploaded documents..."
}
```

Informational responses still return `200 OK`:

| Scenario | Answer |
|---|---|
| No relevant vector chunks | `"No relevant financial data found in your document vault."` |

Error cases:

| Status | Condition |
|---|---|
| `400` | `"Question is required"` or `"Question must be under 1000 characters"` |
| `403` | Authenticated caller has no team: `"This account is not a member of a team"` |
| `502` | AI/LLM integration failure |

---

## 4. Postman-Tested End-To-End Flow

The attached Postman collection exercises this happy path:

1. `POST {{baseUrl}}/onboarding`
   - Body: `{"username":"super_admin","password":"dummy_pass"}`
   - Saves no token directly.
2. `POST {{baseUrl}}/auth/login`
   - Body: `{"username":"super_admin","password":"dummy_pass"}`
   - Saves `accessToken` to `superAdmin_jwtToken`.
3. `POST {{baseUrl}}/teams`
   - Bearer token: `{{superAdmin_jwtToken}}`
   - Body: `{"name":"demo"}`
   - Saves response `id` to `teamId`.
4. `POST {{baseUrl}}/users`
   - Bearer token: `{{superAdmin_jwtToken}}`
   - Body: `{"username":"admin","password":"dummy_pass","role":"ADMIN","teamId":"{{teamId}}"}`
5. `POST {{baseUrl}}/auth/login`
   - Body: `{"username":"admin","password":"dummy_pass"}`
   - Saves `accessToken` to `admin_jwtToken`.
6. `POST {{baseUrl}}/documents`
   - Bearer token: `{{admin_jwtToken}}`
   - Body: `{"fileName":"Python Mastery.pdf","size":518680,"type":"application/pdf"}`
   - Saves `uploadUrl` and `documentId`.
7. `PUT {{uploadUrl}}`
   - No auth.
   - Header: `Content-Type: application/pdf`
   - Body: file bytes.
8. `POST {{baseUrl}}/documents/{{documentId}}/analyze`
   - Bearer token: `{{admin_jwtToken}}`
   - Expected backend response: `202 Accepted`, empty body.
9. `POST {{baseUrl}}/chat`
   - Bearer token: `{{admin_jwtToken}}`
   - Body: `{"question":"What is the info on regex?"}`
   - Response: `{"answer":"..."}`

Do not hardcode Postman environment token values or presigned S3 URLs; they are runtime artifacts.

---

## 5. Client Flow Notes

### 5.1 Document Upload And Analysis

```text
Frontend -> Backend: POST /documents with file metadata
Backend -> Frontend: 201 { documentId, uploadUrl, status: "PENDING" }
Frontend -> S3: PUT uploadUrl with raw file bytes and matching Content-Type
Frontend -> Backend: POST /documents/{documentId}/analyze
Backend -> Frontend: 202 Accepted
Frontend -> Backend: poll GET /documents/{documentId}
Backend -> Frontend: status changes PENDING/PROCESSING -> COMPLETED or FAILED
```

Frontend guidance:

1. Preserve the returned `documentId`.
2. Upload the file bytes directly to `uploadUrl`.
3. Trigger `POST /documents/{id}/analyze` after successful S3 upload unless an S3 event path is configured to trigger analysis automatically.
4. Poll `GET /documents/{id}` until `status` is `COMPLETED` or `FAILED`.
5. Use `lastAnalyzedAt` to show completion time. There is no inline summary field in the document response.

### 5.2 Suggested UI Route Mapping

| Screen | Primary endpoints | Roles |
|---|---|---|
| Onboarding | `POST /onboarding` | Public, first boot only |
| Login | `POST /auth/login` | Public |
| Super admin teams | `GET /teams`, `POST /teams`, `PUT /teams/{id}`, `DELETE /teams/{id}` | `SUPER_ADMIN` |
| User management | `GET /users`, `POST /users`, `PATCH /users/{id}/role`, `DELETE /users/{id}` | `SUPER_ADMIN`, `ADMIN` |
| Vault dashboard | `GET /documents`, `POST /documents`, `DELETE /documents/{id}` | `ADMIN`, `MEMBER` |
| Document details | `GET /documents/{id}`, `GET /documents/{id}/view`, `POST /documents/{id}/analyze` | `ADMIN`, `MEMBER` |
| Chat | `POST /chat` | `ADMIN`, `MEMBER` |

---

## 6. Endpoint Index

| Method | Path | Auth | Roles | Success |
|---|---|---|---|---|
| `POST` | `/onboarding` | None | Public | `201` |
| `POST` | `/auth/login` | None | Public | `200` |
| `POST` | `/teams` | JWT | `SUPER_ADMIN` | `201` |
| `GET` | `/teams` | JWT | `SUPER_ADMIN` | `200` |
| `PUT` | `/teams/{id}` | JWT | `SUPER_ADMIN` | `200` |
| `DELETE` | `/teams/{id}` | JWT | `SUPER_ADMIN` | `204` |
| `POST` | `/users` | JWT | `SUPER_ADMIN`, `ADMIN` | `201` |
| `GET` | `/users` | JWT | `SUPER_ADMIN`, `ADMIN` | `200` |
| `PATCH` | `/users/{id}/role` | JWT | `SUPER_ADMIN`, `ADMIN` | `200` |
| `DELETE` | `/users/{id}` | JWT | `SUPER_ADMIN`, `ADMIN` | `204` |
| `POST` | `/documents` | JWT | `ADMIN`, `MEMBER` | `201` |
| `GET` | `/documents` | JWT | `ADMIN`, `MEMBER` | `200` |
| `GET` | `/documents/{id}` | JWT | `ADMIN`, `MEMBER` | `200` |
| `GET` | `/documents/{id}/view` | JWT | `ADMIN`, `MEMBER` | `200` |
| `POST` | `/documents/{id}/analyze` | JWT | `ADMIN`, `MEMBER` | `202` |
| `DELETE` | `/documents/{id}` | JWT | `ADMIN`, `MEMBER` | `204` |
| `POST` | `/chat` | JWT | Team member | `200` |

---

## 7. Source References

Controllers:

- `LoginController` - `/api/v1/auth`
- `OnboardingController` - `/api/v1/onboarding`
- `TeamController` - `/api/v1/teams`
- `UserManagementController` - `/api/v1/users`
- `DocumentController` - `/api/v1/documents`
- `ChatController` - `/api/v1/chat`

Security:

- `SecurityConfig` permits `/api/v1/auth/**` and `POST /api/v1/onboarding`; all other backend endpoints require authentication.
- `RequireSuperAdmin`, `RequireAdminOrSuperAdmin`, and `RequireTeamMember` enforce method-level roles.

Errors:

- `GlobalExceptionHandler` maps validation, authentication, authorization, not-found, conflict, and AI integration failures to JSON error responses.
