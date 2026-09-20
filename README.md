# LegacyForge

**Agentic AI that migrates Java 8 monoliths to Spring Boot 3 + Angular 18, with human in the loop review.**

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-18-red)](https://angular.dev/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue)](https://spring.io/projects/spring-ai)
[![Deploy](https://img.shields.io/badge/deploy-Cloud%20Run-4285F4)](https://cloud.google.com/run)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)]()

---

## The problem

Enterprises are sitting on billions of lines of Java 8, JSP, Struts, and legacy Spring MVC code. Rewriting it by hand takes years and rarely finishes. Doing it with a raw LLM chat window produces code that compiles but silently changes behavior. Neither works.

## What LegacyForge does

You point LegacyForge at a legacy Java repo (upload a zip or paste a GitHub URL). A team of AI agents analyzes the codebase, generates a phased migration plan with risk scoring, and executes the migration file by file. Every change goes through a diff review UI where you approve, edit, or roll back. When you're happy, LegacyForge produces a Dockerfile, a CI workflow, and a pull request to your target repo.

Not a chat wrapper. A real engineering pipeline with agents, tools, RAG, tests, diffs, and rollback.

## Demo

_(GIFs and demo video coming in Week 12. Placeholders below.)_

| Ingestion + Analysis | Migration Plan | Live Multi-Agent Execution | Diff Review |
| :---: | :---: | :---: | :---: |
| _(gif)_ | _(gif)_ | _(gif)_ | _(gif)_ |

## Features

- **Repo ingestion** via zip upload or GitHub URL clone
- **Static analysis** with AST parsing, dependency graphs, framework fingerprinting (Struts, Spring MVC 4, JSP, JSF, EJB)
- **RAG indexed codebase** with pgvector for semantic retrieval during migration
- **LLM generated migration plan** with phase ordering and per file risk scoring
- **Multi-agent execution**: AnalyzerAgent, RefactorerAgent, TestGeneratorAgent, ReviewerAgent
- **Real time progress** over WebSocket (STOMP) with per file state updates
- **Diff review UI** with Monaco Editor side by side comparison
- **Auto generated JUnit 5 tests** for migrated code, including Testcontainers for DB layer
- **Confidence scoring** with automatic flagging of low confidence changes for human review
- **Rollback plan** generated per phase with cross referenced tests
- **Deploy artifacts**: auto generated Dockerfile, GitHub Actions workflow, and pull request creation
- **Cost tracking** per migration job (LLM tokens, wall time)
- **Multi provider LLM support** (Groq, Gemini, Mistral, OpenAI, Anthropic) via Spring AI

## Tech stack

**Backend**
- Java 21, Spring Boot 3.3, Spring Security 6, Spring Data JPA, Spring AI 1.0
- JavaParser (AST), JGraphT (dependency graph), java-diff-utils (diffs)
- Flyway (schema migrations), Testcontainers (integration tests), JUnit 5, Mockito
- WebSocket + STOMP for live updates

**Frontend**
- Angular 18 (standalone components, signals)
- NgRx SignalStore, Angular Material, Tailwind CSS
- Monaco Editor for diff viewing
- STOMP client over WebSocket

**AI / RAG**
- Spring AI as the provider abstraction
- Groq (primary, Kimi K2 / GPT-OSS-120B) for agent calls
- Gemini 2.5 Flash (secondary) for large context planning
- Optional Mistral Codestral for code specific tasks
- pgvector for embeddings, `text-embedding-3-small` (OpenAI) or `nomic-embed-text` (local) for vectors

**Data**
- Supabase Postgres (primary DB + pgvector)
- Upstash Redis (job queue, cache)
- Supabase Storage (repo blobs, migration artifacts)

**Infra**
- Docker + Docker Compose (local dev)
- Google Cloud Run (backend hosting, scales to zero)
- Vercel (Angular frontend)
- GitHub Actions (CI/CD)
- GitHub Container Registry (Docker images)
- OpenTelemetry + Grafana Cloud (Loki logs, Tempo traces, Prometheus metrics)

## Architecture at a glance

```
                          ┌────────────────────────────┐
                          │      Angular 18 (Vercel)   │
                          │  Dashboard · Wizard · Diff │
                          └──────────────┬─────────────┘
                                         │ HTTPS + WebSocket (STOMP)
                                         ▼
                          ┌────────────────────────────┐
                          │  Spring Boot 3 (Cloud Run) │
                          │  Auth · Ingestion · Analysis│
                          │  RAG · Planning · Migration │
                          │  Review · Notifications     │
                          └──┬────────┬──────────┬──────┘
                             │        │          │
             ┌───────────────┘        │          └───────────────┐
             ▼                        ▼                          ▼
   ┌──────────────────┐    ┌────────────────────┐     ┌────────────────────┐
   │ Supabase Postgres│    │  Upstash Redis     │     │  Groq / Gemini     │
   │ + pgvector       │    │  job queue + cache │     │  LLM inference     │
   └──────────────────┘    └────────────────────┘     └────────────────────┘
```

See [`docs/architecture.md`](docs/architecture.md) for the full breakdown.

## Quick start (local dev)

**Prerequisites**
- Java 21 (`sdk install java 21.0.4-tem`)
- Node 20 (`nvm install 20`)
- Docker Desktop
- A Groq API key ([console.groq.com/keys](https://console.groq.com/keys), free, no card required)
- Optional: a Google AI Studio key for Gemini

**Clone and run**

```bash
git clone https://github.com/vinay27-code/legacyforge.git
cd legacyforge
cp .env.example .env
# Edit .env with your Groq key
docker compose up -d          # Postgres + Redis + pgAdmin
cd backend && ./mvnw spring-boot:run &
cd ../frontend && npm install && npm start
```

Open `http://localhost:4200`. Create an account. Upload a legacy Java 8 zip (there's a JPetStore sample in `legacy-samples/`). Watch the agents work.

## Roadmap

| Phase | Weeks | Deliverable |
| :--- | :--- | :--- |
| Foundation | 1 | Repo scaffolding, deploy pipeline, hello world live |
| Auth + Shell | 2 | Login, JWT, dashboard skeleton |
| Ingestion | 3 | Zip upload, GitHub clone, file tree |
| Analysis | 4 | AST parsing, framework detection, dep graph |
| RAG | 5 | Chunking, embeddings, semantic retrieval |
| Planning | 6 | LLM migration plan with risk scoring |
| Migration Agents (Part 1) | 7 | AnalyzerAgent + RefactorerAgent |
| Migration Agents (Part 2) | 8 | TestGenerator + Reviewer, full batch execution |
| Review UI | 9 | Diff viewer, approve/edit/rollback |
| Deploy Tab | 10 | Auto Dockerfile, CI workflow, PR creation |
| Hardening | 11 | Observability, security, load testing |
| Demo + Launch | 12 | Demo video, HackerNews Show HN, LinkedIn post |

## Author

**Vinay Babu Machha**
Full Stack AI Developer at HCLTech · MS in Information Technology, Arizona State University
[GitHub](https://github.com/vinay27-code) · [Email](mailto:vinaybabumachha@gmail.com)

## License

MIT
