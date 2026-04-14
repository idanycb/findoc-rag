# FinDoc Analyzer Frontend

Modern Next.js frontend for document ingestion, audit visibility, and AI-assisted Q&A.

## For Users

- Sign in to access your workspace.
- Upload documents and monitor processing status.
- Supports document upload, insight viewing, and deletion.
- Provides an audit log view for archival/compliance tracking.
- Enables chat-based financial Q&A grounded in uploaded documents.

## Main Screens

- `/login` - authentication
- `/dashboard` - document vault and document actions
- `/audit` - audit/compliance history
- `/chat` - AI analyst conversation

## For Developers

## Tech Stack

- Next.js 16 (App Router)
- React 19 + TypeScript
- Tailwind CSS 4
- ESLint 9
- Prettier 3

## Prerequisites

- Node.js 20+
- pnpm 9+
- Running backend API for authentication, documents, and chat

## Quick Start

```bash
pnpm install
pnpm dev
```

Open the local app URL shown by Next.js in the terminal.

## Available Scripts

```bash
pnpm dev      # Start development server
pnpm build    # Create production build
pnpm start    # Start production server
pnpm lint     # Run ESLint
pnpm format   # Format code with Prettier
pnpm format:check # Check code formatting
```

## Integration Notes

- Frontend API requests are proxied through Next.js rewrites.
- Route protection and redirects are handled in middleware-style proxy logic.
- Keep backend base URL environment-specific for non-local deployments.

## Project Structure

- `src/app` - App Router pages and layouts
- `src/features` - domain UI (vault, audit, chat, upload, insights)
- `src/context` - auth state and logout behavior
- `src/hooks` - document data hooks
- `src/lib` - API helpers and shared types
