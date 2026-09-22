# Week 7: Migration agents

Goal: for every file the Week 6 plan touches, spawn an LLM agent that actually **generates the modernized Spring Boot / Angular equivalent**. Runs in parallel (6 workers). Each file becomes a `MigrationArtifact` with legacy source next to generated source. New "Migration agents" tab in the UI shows per-phase status counters and lets you click any file to see the side-by-side diff.

No new secrets or providers needed: the agents reuse the existing `LlmProvider` (points at OpenAI's `gpt-4o-mini` in prod, Ollama's `llama3.2` locally). The `LlmProvider` gets a new `completeText()` method for plain-text (source code) responses in addition to the existing `completeJson()` method the planner uses.

## 1. Extract the zip

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week7.zip -d /tmp/lf-w7
cp -R /tmp/lf-w7/legacyforge-w7/backend/. backend/
cp -R /tmp/lf-w7/legacyforge-w7/frontend/. frontend/
cp -R /tmp/lf-w7/legacyforge-w7/docs/. docs/
rm -rf /tmp/lf-w7
git status
```

Should show: new files under `backend/src/main/java/com/legacyforge/agents/`, a modified `backend/src/main/java/com/legacyforge/planning/service/LlmProvider.java` (added `completeText()`), new `V8__migration_artifacts.sql`, new `frontend/src/app/features/agents/`, and a modified `repo-detail.component.ts` (5th tab wired).

## 2. Run backend locally

```bash
cd ~/dev/legacyforge
set -a && source .env && set +a
lsof -ti :8080 | xargs kill -9 2>/dev/null
cd backend
mvn spring-boot:run
```

Watch for `Successfully applied 1 migration to schema "public", now at version v8`.

## 3. Frontend

```bash
cd ~/dev/legacyforge/frontend
npm start
```

Open http://localhost:4200. Log in, open jpetstore. You should see **five tabs** now: Files, Analysis, Semantic search, Migration plan, **Migration agents**.

## 4. Test the flow locally

1. First, make sure a plan exists: **Migration plan** → **Generate migration plan** (if you don't already have one).
2. Then click the **Migration agents** tab.
3. Click **Run migration agents**.

Locally against llama3.2, expect **2 to 8 minutes** for jpetstore (52 files × 6 parallel × 5-15s each). llama3.2 will produce rough code and some files may fail — that's fine, the UI shows counts of success/failed per phase. Click any successful file to see the side-by-side diff.

In prod against `gpt-4o-mini`, expect **30 to 90 seconds** and much better code.

## 5. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git status
git commit -m "Week 7: migration agents (parallel per-file code generation + side-by-side diff UI)"
git push
gh run watch
```

Wait for all four green.

## 6. Verify prod

```bash
gcloud run services logs read legacyforge-backend \
  --project=$GCP_PROJECT_ID \
  --region=us-central1 \
  --limit=40 \
  | grep -i "LlmProvider:"
```

Should still show `base=https://api.openai.com/v1 model=gpt-4o-mini` (unchanged from Week 6).

Then open https://legacyforge-git-main-vinay-1684.vercel.app/, log in, open jpetstore, click **Migration agents**, click **Run migration agents**. Watch the counters climb.

## Notes on the design

- **Parallelism**: `MigrationAgentService` uses a fixed pool of 6 workers. On jpetstore (52 files) this finishes in ~10-15s of pure LLM time on OpenAI. If you migrate a much bigger repo, bump `PARALLELISM` in `MigrationAgentService.java`.
- **Prompt output contract**: the LLM's first line must be `// TARGET: <path>`. The service extracts that as the proposed target path and stores the rest as generated code.
- **Rerunning is destructive**: `runAgainstPlan` deletes all previous artifacts for the repo before starting. So every run gives you a fresh set.
- **HTTP timeout**: Cloud Run defaults to 300s per request. The parallel run fits inside that. If you ever migrate a repo big enough to exceed it, we'll move the run to a background thread and add a status polling endpoint.
- **Cost**: on OpenAI `gpt-4o-mini`, one full jpetstore run is roughly 100k tokens across all files, about $0.02.

## Common issues

- **"No plan yet — generate one first."**: the agents need a stored plan. Go to Migration plan tab and generate.
- **Every file FAILS with the same error**: LLM provider config wrong. Check `LlmProvider:` line in startup logs.
- **Most files FAIL with "Read timed out"**: OpenAI is slow that minute. Rerun.
- **Diff dialog shows raw generated code without a TARGET line**: the model didn't follow the format. That artifact stores `targetPath=null` but the code is still there.

## What's next

Week 8: **Compilation validation.** Take each generated Java file, run it through `javac` in an isolated classloader, capture compile errors, and feed those back to the agent for a retry (self-healing loop). Angular files get the same treatment via `ng build --dry-run`. Green = shippable code.
