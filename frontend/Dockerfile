# --- Stage 1: Build ---
FROM node:20-alpine AS builder
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@latest --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
ENV NEXT_TELEMETRY_DISABLED 1

ARG NEXT_API_DESTINATION
ENV NEXT_API_DESTINATION=$NEXT_API_DESTINATION

RUN pnpm build

# --- Stage 2: Distroless Node.js Runtime ---
FROM gcr.io/distroless/nodejs20-debian12
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED 1

# Distroless nodejs image automatically uses 'nonroot' user
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

USER nonroot
EXPOSE 3000
ENV PORT 3000

# The distroless node image entrypoint is 'node'
CMD ["server.js"]