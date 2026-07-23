# FinDoc Analyzer Frontend

Modern Next.js frontend for document ingestion, SEC EDGAR filing import, and AI-assisted Q&A.

## For Users

- Sign in to access your workspace.
- Upload documents or import SEC EDGAR filings and monitor processing status.
- Supports document upload, filing browse/import, insight viewing, and deletion.
- Enables chat-based financial Q&A grounded in indexed workspace documents and filings.

## Main Screens

- `/login` - authentication
- `/vault` - document vault and document actions
- `/edgar` - SEC company search and filing import
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
- Running backend API for authentication, documents, EDGAR, and chat

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
- `src/features` - domain UI (vault, EDGAR, chat, upload)
- `src/context` - auth state and logout behavior
- `src/shared/hooks` - document data hooks
- `src/shared/lib` and `src/shared/types` - API helpers and shared types
