# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

ArtFetch is a full-stack web application that scrapes artwork auction data from the Artron platform (artso.artron.net), stores it in PostgreSQL, and provides a React UI for managing scrape tasks and exporting results to Excel.

## Commands

### Frontend (in `frontend/`)
```bash
npm run dev      # Dev server on port 5173 (proxies /api → localhost:8080)
npm run build    # TypeScript check + Vite production build
npm run preview  # Serve production build locally
```

### Backend (in `backend/`)
```bash
mvn spring-boot:run              # Run locally (requires PostgreSQL)
mvn package -DskipTests          # Build JAR
mvn test                         # Run tests
```

### Full Stack (Docker)
```bash
docker-compose up                # Start PostgreSQL + backend + frontend
docker-compose down              # Tear down
```

## Required Post-Change Workflow

After any code change, the updated code must be compiled and the corresponding service must be restarted so the latest code actually takes effect.

- **After frontend code changes**:
  Run `npm run build` in `frontend/`, then restart the frontend service or container so the new frontend bundle is live.
- **After backend Java code changes**:
  Rebuild the backend with `mvn package -DskipTests` (or the project’s active backend build command), then restart the backend service or container so the latest Java code is running.
- **When both frontend and backend are changed**:
  Rebuild both sides and restart both services.
- Do not treat the task as complete until the relevant compile step and restart step have both been performed, unless the user explicitly says not to restart services.

## Architecture

### Backend (Spring Boot 3.2, Java 17)
Layered: **Controllers → Services → Repositories → JPA Entities → PostgreSQL**

Key services:
- **`TaskService`** — Creates/manages scrape tasks; uses `ExecutorService` with a `ConcurrentHashMap` to track live threads. Supports pause/resume via thread interruption.
- **`FetchService`** — Scrapes Artron with jsoup. Two-phase: list pages (extract item IDs + basic metadata) then detail pages (enrich each record). 1-second delay between requests. Uses regex to extract total page count and JS-embedded image arrays.
- **`ExportService`** — Generates `.xlsx` via Apache POI from filtered artwork queries.
- **`auth/*`** — Sa-Token based application authentication. Users receive permissions through roles; controllers enforce API permissions with `@SaCheckPermission`, and service-layer checks must be added for data-scope rules.

Task lifecycle states: `PENDING → RUNNING ↔ PAUSED → COMPLETED | FAILED | CANCELLED`

Config properties (`application.yml` / env vars):
- `artfetch.source.base-url` — Artron search URL
- `artfetch.source.request-delay-ms` — Delay between HTTP requests (default 1000ms)
- `artfetch.task.max-concurrent-tasks` — Max parallel tasks (default 5)
- `artfetch.task.thread-pool-size` — Worker thread pool size (default 10)

### Frontend (React 18, TypeScript, Ant Design 5)
Three pages wired via React Router 6:
- **`TasksPage`** — List/create/control scrape tasks. Polls task status every 5 seconds.
- **`ArtworksPage`** — Browse/filter artworks; triggers Excel export via `/api/artworks/export`.
- **`ArtworkDetailPage`** — Single artwork view.
- **Auth pages** — Login, user management, role permission management, and audit logs.

All API calls go through `src/api/index.ts` (Axios instance with error interceptors). TypeScript interfaces live in `src/types/index.ts`.

## Authorization Requirements for Future Work

Any new feature must explicitly consider permissions before implementation is considered complete:

- Add or reuse a stable permission code for each new backend API, user-facing page, and privileged button/action.
- Enforce permissions on backend controllers with Sa-Token annotations such as `@SaCheckPermission`; frontend button hiding is only a UX aid and is never sufficient by itself.
- Add service-layer data-scope checks whenever access depends on the current user, role, ownership, expert assignment, auditor assignment, or project state.
- Seed new built-in permission codes in `AuthDataInitializer`, then update default role mappings deliberately.
- Update frontend route guards, menu visibility, and button visibility through the auth context permission helpers.
- For protected binary resources such as images and exports, use authenticated API requests rather than bare URLs that cannot carry the `Authorization` header.
- Record audit logs for sensitive operations such as data export, destructive actions, user/role changes, password resets, approval, rejection, and permission changes.
- Keep `docs/design-auth-sa-token.md` and related PRD/design docs in sync when adding or changing permission boundaries.

### Data Model
- **`SearchTask`** — Tracks a scrape job (keyword, status, currentPage, totalPages, totalFetched).
- **`Artwork`** — Scraped record. Upserted by `externalId`. Key fields: title, artist, medium, dimensions, year (auction date), collection (auction company), valuation, imageUrl, sourceUrl. Indexed on `task_id` and `external_id`.

### Deployment
Docker Compose runs three services:
1. **postgres** (port 5432) — health-checked before other services start
2. **backend** (port 8080) — connects to postgres via env vars
3. **frontend** (port 3000) — Nginx serves compiled React app and reverse-proxies `/api` to backend

Copy `.env.example` to `.env` and fill in PostgreSQL credentials before running Docker.
