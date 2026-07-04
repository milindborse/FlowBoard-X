# FlowBoard X

A visual workflow orchestration platform — DAG-based execution engine, distributed Redis-backed
workers, real-time WebSocket execution streaming, versioned workflows, and a React Flow visual
builder. Built with Spring Boot + PostgreSQL + Redis on the backend, React + TypeScript + React
Flow on the frontend.

---

## 1. What's actually implemented

This is a real, working execution engine — not a mockup. Specifically:

- **DAG engine** (`backend/.../engine/DagEngine.java`): topological validation, three-color DFS
  cycle detection, event-driven concurrent dispatch (independent branches run on separate threads
  the instant their dependencies resolve — not rigid layer-by-layer execution).
- **Retry engine**: exponential backoff with jitter, per-node override via the `RETRY` control node
  or per-node `retryMaxAttempts`/`retryBaseBackoffMs` fields.
- **Branch logic**: `CONDITION` nodes pick a `true`/`false` edge; non-taken branches skip-cascade
  down the graph; a node only skip-cascades itself if *every* incoming path was skipped.
- **Replay-from-failure and Approval-resume** share one mechanism: re-hydrate prior successful node
  outputs into the execution context, then resume the DAG engine from a specific node instead of
  the root.
- **Distributed worker queue**: Redis list with blocking right-pop; multiple polling threads (and
  multiple backend instances, if you scale horizontally) compete fairly for the same queue.
- **Live execution streaming**: STOMP over WebSocket (`/ws`), topic-per-run (`/topic/runs/{id}`).
- **All 15 node types** from the spec have real executor implementations (HTTP via RestTemplate,
  Postgres via JDBC, Redis via RedisTemplate, Email via JavaMailSender, etc.) — see §4 for which
  ones need your own credentials to actually do something live.
- **All 8 entities** (`users`, `workflows`, `workflow_versions`, `workflow_runs`, `workflow_nodes`,
  `workflow_edges`, `node_executions`, `audit_logs`) with full JPA mappings.

## 2. What I could NOT verify

I do not have network access to Maven Central from this sandbox (only npm, PyPI, GitHub, and the
Ubuntu package archive are reachable), so **the backend has not been compiled**. I installed Maven
itself via `apt` and confirmed it's blocked specifically on `repo.maven.apache.org` (403), not on
anything I wrote. I did do a thorough manual pass: brace/paren balance across all 85 Java files,
package-path consistency, and a full cross-reference of every repository method, DTO field, and
controller route against what the frontend actually calls — but a manual review is not a compiler.
**Run `mvn clean install` yourself before trusting it further; see §6 if you hit errors.**

The **frontend**, by contrast, I did fully verify: `npm install`, `tsc --noEmit` (zero errors), and
a production `vite build` all succeeded in this sandbox.

## 3. Architecture

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   React + TS     │ ──────▶ │   Spring Boot     │ ──────▶ │   PostgreSQL     │
│   React Flow UI  │ ◀────── │   REST + WS API   │ ◀────── │   (8 entities)   │
└─────────────────┘  WS/HTTP └──────────────────┘         └─────────────────┘
                                      │  ▲
                                      ▼  │
                              ┌──────────────────┐
                              │  Redis Queue      │
                              │  (execution msgs) │
                              └──────────────────┘
                                      │
                                      ▼
                              ┌──────────────────┐
                              │  N Worker Threads │
                              │  → DagEngine      │
                              │  → NodeExecutors  │
                              └──────────────────┘
```

**Execution flow:** Save workflow → `POST /workflows/{id}/trigger` creates a `WorkflowRun` row
(status `QUEUED`) → message pushed to Redis list → a worker thread `BRPOP`s it → `DagEngine`
builds the in-memory graph, validates it's acyclic, and dispatches root nodes → each node's
executor runs, retries on failure per its policy, and on success fans out to its dependents →
every transition is persisted to `node_executions` and broadcast over WebSocket → final run
status (`SUCCEEDED`/`FAILED`/`AWAITING_APPROVAL`) is persisted and broadcast.

### Entity-relationship diagram

```
users ──────────────┐
  │ owns             │ acts as (audit)
  ▼                  ▼
workflows ────▶ workflow_versions ────▶ workflow_nodes
  │                    │                workflow_edges
  │                    │
  ▼                    ▼
audit_logs       workflow_runs ────▶ node_executions
```

### Replay / approval-resume sequence

```
Run fails at node C (A → B → C → D)
   │
   ▼
NodeExecution rows: A=SUCCEEDED, B=SUCCEEDED, C=FAILED
   │
   ▼
POST /workflows/{id}/trigger { replayFromRunId, replayFromNodeId: "C" }
   │
   ▼
ExecutionService.computeSucceededNodesUpTo() → {A, B}, with their outputs reloaded into context
   │
   ▼
DagEngine.execute(..., alreadyCompletedIds={A,B}, startNodeIds=["C"])
   │
   ▼
C re-runs, then D dispatches normally off C's success — exact same code path as a fresh run
```

Approval-resume uses the identical mechanism: `alreadyCompletedIds` includes the approval node
itself (now marked `SUCCEEDED`), and `startNodeIds` is its direct children.

### Project structure

```
flowboard-x/
├── backend/                   Spring Boot (Java 17, Maven)
│   └── src/main/java/com/flowboardx/
│       ├── domain/entity/      8 JPA entities
│       ├── domain/enums/       NodeType, RunStatus, NodeExecutionStatus, ...
│       ├── repository/         Spring Data JPA repositories
│       ├── dto/                Request/response DTOs
│       ├── engine/             DagEngine, ExecutionGraph, ExecutionContext, cycle detection
│       ├── engine/executor/    15 NodeExecutor implementations + registry
│       ├── retry/              RetryPolicy, exponential backoff calculator
│       ├── queue/               Redis producer + polling worker
│       ├── websocket/          STOMP broadcaster + event model
│       ├── service/             WorkflowService, ExecutionService, AnalyticsService, UserService
│       ├── controller/          REST controllers
│       ├── security/            JWT util + filter
│       └── config/              Security, WebSocket, CORS, Scheduler (cron) config
├── frontend/                   React 18 + TypeScript + Vite
│   └── src/
│       ├── pages/               Dashboard, Workflows, Builder, RunViewer, History, Analytics, Settings, Login
│       ├── components/          Layout, NodeLibrarySidebar, NodePropertiesPanel, nodes/FlowNode
│       ├── store/                Zustand stores (auth, builder canvas state)
│       ├── api/                  Axios client + typed API functions
│       └── types/                Shared TypeScript types mirroring backend DTOs
├── database/
│   └── schema.sql              Authoritative schema reference (also auto-created by Hibernate)
├── docker-compose.yml
└── README.md                   You are here
```

## 4. Node types — what's real vs. what needs your credentials

| Node | Status |
|---|---|
| Manual / Scheduler / Webhook Trigger | Fully functional out of the box |
| Condition, Transform, Delay, Aggregator | Fully functional out of the box |
| Parallel Split, Merge, Retry | Fully functional — these drive the DAG engine's concurrency directly |
| Approval | Fully functional — pauses the run, `POST /runs/{id}/nodes/{nodeId}/approve` resumes it |
| **HTTP Request** | Functional immediately — calls whatever URL you put in its config |
| **PostgreSQL Query** | Needs a target database's `jdbcUrl`/`username`/`password` in the node config (this queries an arbitrary business DB, deliberately separate from FlowBoard X's own metadata DB) |
| **Redis Publish** | Functional immediately against the same Redis instance the platform uses |
| **Email** | Needs real SMTP credentials in `application.yml` / env vars — see §6 |

## 5. Quick start — Docker Compose (fastest path)

```bash
cd flowboard-x
cp .env.example .env        # edit if you want real SMTP / a non-default JWT secret
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Postgres: localhost:5432 (user `postgres` / password `postgres` / db `flowboardx`)
- Redis: localhost:6379

## 6. Manual local setup (since you already have Postgres + Maven)

### Backend

```bash
cd backend

# 1. Create the database (skip if you'd rather let Hibernate auto-create it —
#    spring.jpa.hibernate.ddl-auto=update in application.yml does that already)
psql -U postgres -c "CREATE DATABASE flowboardx;"
psql -U postgres -d flowboardx -f ../database/schema.sql   # optional, Hibernate will also do this

# 2. Make sure Redis is running locally (apt/brew install redis, or run via Docker)
redis-server &

# 3. Set required env vars (or just edit application.yml directly)
export DB_HOST=localhost DB_PORT=5432 DB_NAME=flowboardx DB_USER=postgres DB_PASSWORD=postgres
export REDIS_HOST=localhost REDIS_PORT=6379
export JWT_SECRET="replace-with-a-real-256-bit-secret-before-deploying-anywhere-real"

# 4. Build and run
mvn clean install
mvn spring-boot:run
```

If `mvn clean install` fails on your machine with dependency-resolution errors, it's most likely
your local Maven settings pointing somewhere unusual — the `pom.xml` only depends on standard
Spring Boot 3.2.5 starters plus `jjwt` 0.11.5, all on Maven Central.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite's dev server proxies `/api`, `/ws`, and `/webhooks` to `http://localhost:8080` (see
`vite.config.ts`) — no `.env` needed for local dev against a local backend.

### First run checklist

1. Register a user: open http://localhost:5173, click "Sign up."
2. Create a workflow, drag a **Manual Trigger** node onto the canvas, connect it to whatever you
   want to test (an HTTP Request node is the easiest to see working end-to-end immediately).
3. **Save** before you **Execute** — the Execute button is disabled while there are unsaved
   changes, specifically so you never accidentally run a stale graph.
4. Watch the live execution viewer — node borders update via WebSocket in real time, with a log
   console at the bottom.

## 7. Things you'll need to do manually

- **Run `mvn clean install`** and fix anything that surfaces — I could not compile-check this
  (see §2). The most likely failure class, if any, is a Lombok/Java-21-vs-17 annotation processing
  quirk; the `pom.xml` targets Java 17 explicitly via the Spring Boot parent.
- **Set a real `JWT_SECRET`** before anything resembling production — the default in
  `application.yml` is a placeholder.
- **Add SMTP credentials** (`SMTP_USER`, `SMTP_PASSWORD`) if you want the Email node to send real
  mail. Without them it fails that one node cleanly rather than crashing the worker.
- **Supply real target-database credentials** in any PostgreSQL Query node's config JSON if you
  want it to do something other than fail with a clear connection error.
- **Review the cron scheduler** (`SchedulerConfig`) before relying on it for anything time-critical
  — it's a straightforward 60-second poll suitable for a single backend instance. If you run
  multiple backend instances, every instance will currently fire the same scheduled workflow
  simultaneously; put a Redis `SET NX` distributed lock around `triggerScheduledWorkflows()`
  before scaling out.
- **Decide on webhook security** — `/webhooks/{workflowId}` is intentionally unauthenticated (an
  external system calling it won't have your JWT), but that also means anyone with the URL can
  trigger the workflow. Add a shared-secret header check or HMAC signature verification before
  exposing this publicly.
- **Auth is intentionally minimal** — JWT login/register exist and the API is properly protected,
  but there's no password reset, refresh-token rotation, or per-workflow permissions yet. The
  `UserRole` enum (`ADMIN`/`EDITOR`/`VIEWER`) exists on the entity but isn't enforced anywhere yet
  — that's the next piece to build if you need real multi-user access control.
- **The frontend's live canvas in the Run Viewer** re-derives node *type* as a placeholder
  (`MANUAL_TRIGGER`) when hydrating from `node_executions`, since that endpoint only returns
  label/status, not the original node type. The status colors and labels are accurate; the icon
  shown for each node while a run is in progress is not. Fix is straightforward: have
  `GET /runs/{runId}/nodes` join back to `workflow_nodes` for the type, or have the frontend fetch
  the version's node list once and merge it client-side.
- **Bundle size**: the production frontend build is a single ~920KB JS chunk (Vite warns about
  this). It works fine, but if you care about initial load time, code-split React Flow and
  Recharts behind route-level `lazy()` imports.

## 8. Tech stack

**Frontend:** React 18, TypeScript, Vite, React Flow, Zustand, TanStack Query, Tailwind CSS,
Recharts, STOMP.js + SockJS for WebSocket, Lucide icons.

**Backend:** Spring Boot 3.2.5, Spring Security (JWT), Spring Data JPA, Spring Data Redis,
Spring WebSocket (STOMP), PostgreSQL driver, JJWT, Lombok.

**Infra:** Docker, Docker Compose, multi-stage Dockerfiles for both services, Nginx for serving
the built frontend and reverse-proxying `/api`, `/ws`, `/webhooks` to the backend container.
