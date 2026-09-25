# LegacyForge

**An agentic AI platform that migrates legacy Java monoliths to modern Spring Boot 3 + Angular 18.**

Point it at a legacy repo. It analyzes the code, embeds every file in pgvector for semantic search, plans a phased migration with per-file risk scoring, spawns parallel LLM agents that generate the modernized equivalents, validates every generated file, builds a cross-file dependency graph, and closes plan gaps with a self-healing feedback loop.

Everything runs in prod on Google Cloud Run, Supabase, Vercel, and OpenAI. Live at [legacyforge.vercel.app](https://legacyforge.vercel.app).

---

## Screenshots

<img width="1512" height="949" alt="Image" src="https://github.com/user-attachments/assets/bce13c7a-857a-44da-85b8-110aaf2a8599" />

<img width="1512" height="949" alt="Image" src="https://github.com/user-attachments/assets/78203052-2c31-41e7-8e35-c629a3039a36" />

<img width="1512" height="949" alt="Image" src="https://github.com/user-attachments/assets/5832bfe6-a764-4459-b0f2-5cf23569e1c1" />

---

## Why this exists

Enterprise codebases are stuck on 15-year-old Java stacks (Struts 1.x, iBATIS, JSP, servlet containers). Migrating them by hand takes years. LegacyForge automates the analysis + planning + code generation steps so a small team can move a monolith in weeks, not quarters.

More importantly, it handles the analytical toil that scales poorly as codebases grow: dependency-ordering the migration, tracking cross-file references, and catching gaps where a class is referenced but never planned for.

---

## What it does

**1. Ingest** — clone or upload a Java repo. Every file's content, path, language, and size are stored.

**2. Analyze** — JavaParser AST walks every `.java` file to compute cyclomatic complexity, framework fingerprints (Struts / iBATIS / JSP / web.xml detection), and a top-complex file ranking.

**3. Semantic search** — every file is chunked at method boundaries, embedded via OpenAI `text-embedding-3-small` (768 dims), and stored in Supabase pgvector with an ivfflat cosine index. Queries like "where is the user login logic?" return actual `SignonForm.jsp` and `AccountService.java` files.

**4. Plan** — the entire chunked codebase goes to an LLM in one prompt. Response is a strict JSON schema: N phases (foundational → data → services → controllers → views), each with an estimated day count, per-file risk score (LOW / MEDIUM / HIGH), and concrete target pattern (e.g., "Convert to @RestController", "Migrate to Spring Data JPA @Repository").

**5. Generate** — one LLM agent per file, six running in parallel. Each agent takes a legacy file + its plan entry and produces the modernized Spring Boot 3 / Angular 18 equivalent. Multi-target LLM responses (Repository + Entity + Service in one shot) are split into derived sibling artifacts.

**6. Validate** — every generated Java file is parsed with JavaParser. If it fails, the agent is called again with the parser's line-and-column errors injected into the prompt. Up to 2 retries per file. Self-healing loop.

**7. Analyze coherence** — after all agents finish, `DependencyAnalyzer` walks every generated class and extracts what it references (imports, field types, method params, `new Foo()`). Each reference becomes an edge in a graph. Edges that don't resolve to another generated class are **broken links** — real bugs in the migration.

**8. Self-complete** — click Patch plan. The broken class names get fed back to the planning LLM, which proposes plan additions (slotted into existing phases or grouped into a gap-fill phase). Rerun the agents. Broken count drops. Loop.

---

## Live architecture

```
┌────────────────────────┐        ┌────────────────────────┐
│  Angular 18 (Vercel)   │  https │  Spring Boot 3 backend │
│  • Standalone comps    │◄──────►│  (Cloud Run, 1Gi, 1CPU)│
│  • Material UI, dark   │  JWT   │  • Java 21, Maven      │
│  • signals/computed    │        │  • JPA + JdbcTemplate  │
└────────────────────────┘        └───────┬────────────────┘
                                          │
                        ┌─────────────────┼─────────────────┐
                        ▼                 ▼                 ▼
             ┌───────────────────┐  ┌──────────┐  ┌──────────────────┐
             │  Supabase Postgres│  │  OpenAI  │  │  GHCR + GitHub   │
             │  • 16 with pgvec  │  │  API     │  │  Actions CI/CD   │
             │  • 11 Flyway migs │  │  • embed │  │  • auto deploy   │
             │  • ivfflat index  │  │  • chat  │  │    on push       │
             └───────────────────┘  └──────────┘  └──────────────────┘
```

**Data flow inside the backend:**

```
  Ingest ─► JavaParser Analysis ─► ChunkingService ─► EmbeddingProvider ──► pgvector
                                                                           │
                                                                           ▼
                                              [Semantic search tab: <=> cosine]
                                                                           │
     ┌─────────────────────────────────────────────────────────────────────┘
     │
     ▼
 PlanningService ── LlmProvider (JSON mode) ──► migration_plans (Gemini/OpenAI, structured)
     │
     ▼                             ┌──── ValidationService (JavaParser)
 MigrationAgentService ── LlmProvider (text)
     │  (6 parallel workers)      └──── retry with errors → self-heal
     ▼
 migration_artifacts ─► DependencyAnalyzer ─► migration_dependencies (graph)
                                                    │
                                                    ▼
                                        broken edges ─► PlanPatchService
                                                            │
                                                            └── back into migration_plans
```

---

## Tech stack

| Layer         | What                                                                 |
| ------------- | -------------------------------------------------------------------- |
| Frontend      | Angular 18 (standalone components, signals), Angular Material, highlight.js |
| Backend       | Spring Boot 3.3.4, Java 21, Spring Security (JWT httpOnly cookie)    |
| Database      | Postgres 16 + pgvector (Supabase in prod, local Docker in dev)       |
| Migrations    | Flyway (V1 → V11)                                                    |
| Embeddings    | OpenAI `text-embedding-3-small` (prod), Ollama `nomic-embed-text` (dev) |
| LLM           | OpenAI `gpt-4o-mini` (prod), Ollama `llama3.2` (dev)                 |
| Code analysis | JavaParser 3.26 (semantic chunking, syntax validation, dep graph)    |
| Orchestration | Java `CompletableFuture` + fixed executor pool (6 workers)           |
| Deploy        | Docker → GHCR → Cloud Run (backend), Vercel (frontend, git-linked)   |
| CI            | GitHub Actions (backend tests → docker build → deploy, frontend build) |
| Secrets       | GCP Secret Manager mounted as Cloud Run env vars                     |

---

## Feature highlights

- **Multi-model architecture** — one `LlmProvider` speaks the OpenAI-compatible `/chat/completions` shape. Local Ollama, prod OpenAI, or Gemini via its OpenAI-compat endpoint. Config-only swap.
- **Semantic chunking** — Java files chunk per method with class context prefix. Non-Java files chunk generically with sliding windows.
- **Self-healing generation** — invalid Java gets sent back with parser errors, up to 2 retries per file.
- **Multi-file splits** — when the LLM emits 3 files in one response, they're split into derived sibling artifacts, each validated separately.
- **Dependency graph** — 176+ edges tracked across a real jpetstore migration. Broken edges surface real bugs.
- **Feedback loop** — broken edges → planning LLM → new file entries → rerun → converge.
- **Cost dashboard** — every LLM call's tokens are stored; the Dashboard shows total spend against OpenAI's current price sheet.
- **Structured logging** — every LLM call emits a single line: `llm_call mode=... model=... durationMs=... promptTokens=... outputTokens=... promptChars=...`. Grepable in Cloud Run logs.

---

## Real numbers from one jpetstore-6 migration

*Numbers from the actual prod database as of Week 11 dashboard screenshot.*

| Metric                  | Value    |
| ----------------------- | -------- |
| Files ingested          | 163      |
| Semantic chunks         | 448      |
| Generated artifacts     | 208      |
| Successful generations  | 117      |
| Files validated Java-clean | 64    |
| Files failed to parse   | 1        |
| Files derived from multi-target splits | 13 |
| Dependency edges tracked | 174     |
| Broken references caught | 18      |
| Total tokens used       | 316,100  |
| **Estimated OpenAI cost** | **$0.06** |

Six cents for a whole legacy Java monolith migrated end-to-end.

---

## Running locally

Requires: Java 21, Node 20, Docker (for Postgres), Ollama.

```bash
git clone https://github.com/vinay27-code/legacyforge.git
cd legacyforge

# 1. Postgres with pgvector
docker run -d --name lf-pg -p 5433:5432 \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=legacyforge \
  pgvector/pgvector:pg16

# 2. Ollama (macOS)
brew install ollama
brew services start ollama
ollama pull nomic-embed-text
ollama pull llama3.2

# 3. Env
cp .env.example .env
# fill in: DB creds, JWT secret

# 4. Backend
cd backend && mvn spring-boot:run

# 5. Frontend
cd frontend && npm install && npm start

open http://localhost:4200
```

Login as `admin@legacyforge.dev` / `admin12345` (dev seed) and click "New migration" to ingest a repo.

---

## Prod deployment

Any push to `main` triggers CI: backend tests → Docker build (pushed to GHCR) → Cloud Run deploy. Frontend is git-linked to Vercel and rebuilds on the same push.

Secrets required in GCP Secret Manager: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`.

Cloud Run runtime SA needs `roles/secretmanager.secretAccessor` on all five.

---

## Roadmap

- [ ] Testcontainers-based DB integration tests (deferred at Week 5)
- [ ] Dependency graph SVG visualization (currently text list)
- [ ] Full `javac` compilation validation (currently syntax-only)
- [ ] Plan editing UI (currently regenerate-only)
- [ ] Multi-repo comparison dashboard
- [ ] Anthropic Claude as a third planner option

---

## Weekly build log

| Week | Focus                                                             |
| ---- | ----------------------------------------------------------------- |
| 1-3  | Ingest, JWT auth, repo tree, file viewer                          |
| 4    | JavaParser AST analysis, framework fingerprinting                 |
| 5    | Semantic chunking, embeddings, pgvector search                    |
| 6    | LLM-driven migration planning with structured JSON output         |
| 7    | Parallel migration agents + side-by-side diff UI                  |
| 8    | JavaParser validation + self-healing retry loop                   |
| 9    | Multi-file splitting + cross-file dependency graph                |
| 10   | Broken-links feedback loop (self-completing migration)            |
| 11   | Platform Dashboard + LLM observability + cost tracking            |
| 12   | README, demo video, polish                                        |

---

## License

MIT. Use it, fork it, learn from it. If you ship a real migration with it, please tell me — I want to hear.

---

## Built by

**[Vinay Babu Machha](https://github.com/vinay27-code)** — Full Stack AI Developer at HCLTech, MS in Information Technology at Arizona State University (May 2026). Currently interviewing for Full Stack / AI / Cloud engineering roles.
