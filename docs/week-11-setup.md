# Week 11: Dashboard + LLM observability

Production polish week. Nothing changes the migration mechanics — this is what turns the platform into something you'd hand an interviewer confidently.

**What's shipped:**

1. **`GET /api/stats`** — one aggregate query rolls up repos, files, chunks, plans, artifacts, dep edges, broken edges, plan patches, artifact-quality breakdown (success/failed/valid/invalid/skipped/derived/retries), and per-source token totals across embedding + planning + agents.

2. **Estimated OpenAI cost** — the backend computes it from stored token counts against OpenAI's current price sheet (`text-embedding-3-small` $0.02/M, `gpt-4o-mini` $0.15/M in, $0.60/M out) and hands the number to the UI as a single big green figure. Interviewers love this.

3. **New Dashboard page** — the previously-empty sidebar "Dashboard" nav item now shows: six stat tiles across the top, a two-column panel (Agent output quality bars + LLM token/cost breakdown), and a Recent activity feed showing the last 10 events across every repo (plans, agent runs, ingests, newest first).

4. **`LlmProvider` timing logs** — every LLM call emits a single structured line: `llm_call mode=... model=... durationMs=... promptTokens=... outputTokens=... promptChars=...`. Grepable in Cloud Run logs.

## What's in the drop

**Backend:**
- `stats/dto/PlatformStats.java` — response shape
- `stats/service/StatsService.java` — aggregate SQL + cost calc
- `stats/controller/StatsController.java` — the endpoint
- `planning/service/LlmProvider.java` — updated to log timing + tokens per call

**Frontend:**
- `dashboard/dashboard.models.ts` + `dashboard.service.ts`
- `dashboard/dashboard.component.ts` — the real Dashboard page

## 1. Extract

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week11.zip -d /tmp/lf-w11
cp -R /tmp/lf-w11/legacyforge-w11/backend/. backend/
cp -R /tmp/lf-w11/legacyforge-w11/frontend/. frontend/
cp -R /tmp/lf-w11/legacyforge-w11/docs/. docs/
rm -rf /tmp/lf-w11
git status
```

The Dashboard component may already exist as a placeholder — that's fine, the copy overwrites it. If your router isn't wiring `/dashboard` to `DashboardComponent`, add the route.

## 2. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git commit -m "Week 11: platform stats endpoint, dashboard page, LLM call timing logs"
git push
sleep 5 && gh run watch
```

## 3. Confirm and view

```bash
gcloud run services logs read legacyforge-backend \
  --project=$GCP_PROJECT_ID \
  --region=us-central1 \
  --limit=40 \
  | grep "llm_call" | head -5
```

Should show structured timing lines from any recent LLM call.

Then in the browser, click **Dashboard** in the left sidebar. You'll see the whole picture of what the platform has done tonight: chunk count, artifact count, token spend, retries fired, plan patches applied, and a scrolling activity feed of every plan/rerun/ingest.

## Story for interviews

> The Dashboard is one aggregate SQL call served by a JDBC-backed StatsService. It rolls up seven tables (repos, files, chunks, plans, artifacts, deps, patches) into six top-level metrics, a per-status quality breakdown, and estimated OpenAI cost computed from stored token counts. Every LLM call the platform makes emits a structured timing log line so latency distributions are visible in Cloud Run without adding a metrics dependency.

## Common issues

- **Stats returns zeros**: fresh database or no runs completed. Ingest a repo and generate a plan to seed.
- **Cost looks off**: OpenAI's price sheet moves. Update the `PRICE_*` constants in `StatsService.java` when they change.
- **Recent activity missing INDEX events**: `repos.created_at` needs to exist. If your V1 schema didn't add it, backfill or skip that section.

## What's next

Week 12: **README + demo video.** Write a proper top-level README explaining what the project does, architecture diagram, how to run it locally, and screenshots. Record a 3-4 minute Loom walking through the whole pipeline end to end (ingest → search → plan → agents → dependency graph → patch → converge). That's the artifact you actually put in job applications and pin on GitHub.
