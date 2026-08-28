# FinDoc Analyzer Frontend

Next.js interface for one-time onboarding, team administration, document ingestion, SEC EDGAR import, and citation-grounded chat.

## Features

- Redirects a new deployment from login to one-time super-admin onboarding.
- Provides role-aware navigation for system administrators, team administrators, and members.
- Creates, renames, and removes teams and manages users within backend-enforced scope.
- Uploads documents directly to presigned S3 URLs and polls their analysis state.
- Searches SEC companies, loads `10-K`, `10-K/A`, `10-Q`, and `10-Q/A` filings, and imports selected filings.
- Renders Markdown chat answers with numbered inline citation links and expandable filing source cards.
- Supports responsive desktop and mobile navigation.

## Routes and access

| Route         | Access                   | Purpose                                                         |
| ------------- | ------------------------ | --------------------------------------------------------------- |
| `/login`      | Public                   | Sign in; redirects to onboarding when the database has no users |
| `/onboarding` | Public until initialized | Create the first `SUPER_ADMIN`                                  |
| `/teams`      | `SUPER_ADMIN`            | Manage teams                                                    |
| `/users`      | `SUPER_ADMIN`, `ADMIN`   | Manage all users or the current team                            |
| `/vault`      | `ADMIN`, `MEMBER`        | Search, upload, inspect, retry, and delete documents            |
| `/edgar`      | `ADMIN`, `MEMBER`        | Browse and import supported SEC filings                         |
| `/chat`       | `ADMIN`, `MEMBER`        | Ask questions grounded in the team vault                        |

The browser stores the JWT in a `SameSite=Lax` cookie. Route guards provide client-side navigation safety; the backend remains the authorization boundary.

## Stack

- Next.js 16.2 with App Router and standalone output
- React 19.2 and TypeScript 5
- Tailwind CSS 4
- React Markdown with GFM and line-break support
- ESLint 9 and Prettier 3
- pnpm 11

## Configuration

| Variable                | Default                        | When it is read      | Purpose                                              |
| ----------------------- | ------------------------------ | -------------------- | ---------------------------------------------------- |
| `NEXT_API_DESTINATION`  | `http://localhost:8080/api/v1` | Build/server startup | Destination for the `/api/:path*` rewrite            |
| `NEXT_PUBLIC_DEMO_MODE` | unset                          | Build time           | When `true`, shows the demo credential hint on login |

`NEXT_API_DESTINATION` must include the backend `/api/v1` base path. The UI always calls same-origin `/api/*`, and Next.js proxies those requests to the configured backend.

Demo mode only changes the login hint. It does not create the displayed `demo` account; the backend deployment must provision it.

## Run locally

Prerequisites: Node.js 24 and pnpm 11. The Docker image pins pnpm `11.8.0` through Corepack.

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000). The default proxy expects the backend at `http://localhost:8080/api/v1`.

To point development at another backend:

```bash
NEXT_API_DESTINATION=http://localhost:8080/api/v1 pnpm dev
```

## Scripts

| Command             | Purpose                                |
| ------------------- | -------------------------------------- |
| `pnpm dev`          | Start the development server           |
| `pnpm build`        | Create the production standalone build |
| `pnpm start`        | Run the production build               |
| `pnpm lint`         | Run ESLint                             |
| `pnpm format`       | Rewrite files with Prettier            |
| `pnpm format:check` | Check formatting without changes       |

There is currently no frontend unit-test command. The local verification baseline is:

```bash
pnpm lint
pnpm format:check
pnpm build
```

## Docker and Compose

Build directly:

```bash
docker build \
  --build-arg NEXT_API_DESTINATION=http://backend:8080/api/v1 \
  -t findoc-frontend .
```

That destination assumes the image runs on a Docker network where the backend is named `backend`; the repository Compose stack creates that topology automatically. `make demo` additionally sets `NEXT_PUBLIC_DEMO_MODE=true` at build time.

## API integration

The shared `apiCall` helper:

- sends JSON requests through `/api`,
- attaches the bearer token when supplied,
- clears the cookie and returns to login after an authenticated `401`, and
- maps backend error responses into `ApiError` messages.

Document upload is the exception: the UI requests a presigned URL from the backend and uploads the binary directly to S3. Analysis status is then polled from the backend.

EDGAR filing browse requests all four supported forms independently, tolerates partial form failures when at least one request succeeds, de-duplicates accessions, and sorts the combined result newest first.

## Layout

```text
src/
  app/                 App Router pages, layouts, and global states
  context/             JWT authentication state
  features/
    admin/             teams and user management
    auth/              login and onboarding
    chat/              RAG chat and citation rendering
    documents/         vault, upload, detail, and status UI
    edgar/             company search, form filters, and import UI
    shell/             desktop and mobile navigation
  shared/
    hooks/             access and document hooks
    lib/               API, auth, document, EDGAR, and route helpers
    types/             frontend API contracts
proxy.ts               coarse route redirects by authentication and role
next.config.ts         backend rewrite and standalone build settings
```
