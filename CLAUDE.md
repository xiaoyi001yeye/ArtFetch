# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Architecture

### Backend (Spring Boot 3.2, Java 17)
Layered: **Controllers → Services → Repositories → JPA Entities → PostgreSQL**

Key services:
- **`TaskService`** — Creates/manages scrape tasks; uses `ExecutorService` with a `ConcurrentHashMap` to track live threads. Supports pause/resume via thread interruption.
- **`FetchService`** — Scrapes Artron with jsoup. Two-phase: list pages (extract item IDs + basic metadata) then detail pages (enrich each record). 1-second delay between requests. Uses regex to extract total page count and JS-embedded image arrays.
- **`ExportService`** — Generates `.xlsx` via Apache POI from filtered artwork queries.

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

All API calls go through `src/api/index.ts` (Axios instance with error interceptors). TypeScript interfaces live in `src/types/index.ts`.

### Data Model
- **`SearchTask`** — Tracks a scrape job (keyword, status, currentPage, totalPages, totalFetched).
- **`Artwork`** — Scraped record. Upserted by `externalId`. Key fields: title, artist, medium, dimensions, year (auction date), collection (auction company), valuation, imageUrl, sourceUrl. Indexed on `task_id` and `external_id`.

### Deployment
Docker Compose runs three services:
1. **postgres** (port 5432) — health-checked before other services start
2. **backend** (port 8080) — connects to postgres via env vars
3. **frontend** (port 3000) — Nginx serves compiled React app and reverse-proxies `/api` to backend

Copy `.env.example` to `.env` and fill in PostgreSQL credentials before running Docker.
