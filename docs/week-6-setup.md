# Week 6: LLM-driven migration planning

Goal: hand the entire chunked codebase to an LLM and get back a **phased migration plan** with per-file risk scoring and concrete target patterns. Local dev uses **Ollama** with `llama3.2`. Prod uses **Gemini 2.5 Flash** which has a 1M-token context window (fits jpetstore many times over) and a free tier that comfortably handles planning.

The pattern mirrors Week 5: a single `LlmProvider` speaks the OpenAI-compatible `/chat/completions` shape, so swapping providers is a config change.

## 1. Extract the zip

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week6.zip -d /tmp/lf-w6
cp -R /tmp/lf-w6/legacyforge-w6/backend/. backend/
cp -R /tmp/lf-w6/legacyforge-w6/frontend/. frontend/
cp -R /tmp/lf-w6/legacyforge-w6/docs/. docs/
rm -rf /tmp/lf-w6
```

## 2. Pull a local LLM (for dev)

Ollama's already running from Week 5. Pull a small code-capable model:

```bash
ollama pull llama3.2
```

About 2 GB, downloads in 1-2 minutes. Test it:

```bash
curl http://localhost:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"llama3.2","messages":[{"role":"user","content":"Return JSON: {\"ok\": true}"}],"response_format":{"type":"json_object"}}' \
  | head -c 300
```

Should return a chat completion with a JSON body in `choices[0].message.content`.

## 3. Put the Gemini key in Secret Manager (for prod)

Your `GEMINI_API_KEY` secret is already in Secret Manager from the Week 5 rescue. We just need a **new env var mapping** on Cloud Run so the planning code can find it under `PLANNING_API_KEY`.

If for some reason the secret isn't there anymore, recreate it:

```bash
gcloud secrets versions access latest --secret=GEMINI_API_KEY --project=$GCP_PROJECT_ID | head -c 6
echo
```

You should see `AQ.Ab8`. If the command errors "not found", store the key again:

```bash
read -s GEMINI_KEY   # paste key, hit enter
echo -n "$GEMINI_KEY" | gcloud secrets create GEMINI_API_KEY \
  --data-file=- --project=$GCP_PROJECT_ID
unset GEMINI_KEY
```

Grant Cloud Run's runtime SA access (idempotent, safe to re-run):

```bash
PROJECT_NUMBER=$(gcloud projects describe $GCP_PROJECT_ID --format="value(projectNumber)")
gcloud secrets add-iam-policy-binding GEMINI_API_KEY \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor" \
  --project=$GCP_PROJECT_ID
```

## 4. Wire PLANNING_API_KEY into the CI workflow

Open `.github/workflows/ci-cd.yml`. Find the `--set-secrets` line in the deploy step:

```yaml
--set-secrets "DB_URL=DB_URL:latest,DB_USERNAME=DB_USERNAME:latest,DB_PASSWORD=DB_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest,GROQ_API_KEY=GROQ_API_KEY:latest,EMBEDDING_API_KEY=OPENAI_API_KEY:latest"
```

Add `PLANNING_API_KEY=GEMINI_API_KEY:latest` to the end (the `PLANNING_API_KEY` env var maps to the `GEMINI_API_KEY` secret):

```yaml
--set-secrets "DB_URL=DB_URL:latest,DB_USERNAME=DB_USERNAME:latest,DB_PASSWORD=DB_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest,GROQ_API_KEY=GROQ_API_KEY:latest,EMBEDDING_API_KEY=OPENAI_API_KEY:latest,PLANNING_API_KEY=GEMINI_API_KEY:latest"
```

## 5. Add the planning config to application.yml

Open `backend/src/main/resources/application.yml`. Under the top-level `app:` block, add a `planning:` section that mirrors `embedding:` in style. Full block should look like this (the `embedding:` part already exists):

**Default profile** (local dev, uses Ollama):

```yaml
app:
  admin:
    email: ${ADMIN_EMAIL:admin@legacyforge.dev}
    password: ${ADMIN_PASSWORD:admin12345}
  embedding:
    base-url: ${EMBEDDING_BASE_URL:http://localhost:11434/v1}
    api-key: ${EMBEDDING_API_KEY:ollama}
    model: ${EMBEDDING_MODEL:nomic-embed-text}
  planning:
    base-url: ${PLANNING_BASE_URL:http://localhost:11434/v1}
    api-key: ${PLANNING_API_KEY:ollama}
    model: ${PLANNING_MODEL:llama3.2}
```

**Prod profile** (uses Gemini):

```yaml
  embedding:
    base-url: https://api.openai.com/v1
    api-key: ${EMBEDDING_API_KEY:missing}
    model: text-embedding-3-small
  planning:
    base-url: https://generativelanguage.googleapis.com/v1beta/openai
    api-key: ${PLANNING_API_KEY:missing}
    model: gemini-2.5-flash
```

## 6. Bump Cloud Run timeout for the planning request

The planning HTTP call can take 30-60 seconds because Gemini has to read the whole codebase and generate structured JSON. In `.github/workflows/ci-cd.yml`, make sure the deploy step has (or add):

```yaml
--timeout 300 \
```

We already have `--memory 1Gi` from the Week 5 rescue.

## 7. Run backend + frontend locally

```bash
cd ~/dev/legacyforge
set -a && source .env && set +a
cd backend
mvn clean spring-boot:run
```

Watch for `Successfully applied 1 migration to schema "public", now at version v7` and `LlmProvider: base=http://localhost:11434/v1 model=llama3.2`.

Another terminal:

```bash
cd ~/dev/legacyforge/frontend
npm start
```

Open http://localhost:4200. Log in as admin, click your jpetstore repo. You should see a fourth tab **Migration plan** next to Files, Analysis, and Semantic search.

## 8. Test the flow locally

1. Click **Migration plan** tab
2. Click **Generate migration plan** — takes 60-180s locally with llama3.2 (it's a small model reading a big prompt)
3. Once done, you should see:
   - Chip row: N phases · X days · overall risk · provider
   - Executive summary card
   - Expandable phase panels, each with per-file risk chips (red/yellow/green) and migration notes

Local Ollama plans are usually reasonable-but-brief. Gemini in prod is much better.

## 9. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git status
git commit -m "Week 6: LLM-driven migration planning (Ollama local, Gemini 2.5 Flash prod)"
git push
gh run watch
```

Wait for all four jobs green. Cloud Run redeploys with V7 migration applied.

## 10. Verify prod is using Gemini

```bash
gcloud run services logs read legacyforge-backend \
  --project=$GCP_PROJECT_ID \
  --region=us-central1 \
  --limit=60 \
  | grep -i "LlmProvider"
```

Should print:

```
LlmProvider: base=https://generativelanguage.googleapis.com/v1beta/openai model=gemini-2.5-flash
```

Then open the prod URL, go to jpetstore → Migration plan → **Generate migration plan**. Should complete in 20-40s. The plan Gemini produces is genuinely thoughtful: it groups files sensibly, calls out Struts action classes and iBATIS SQL maps as high-risk, and gives concrete Spring/Angular target patterns for each file.

## Common issues

- **`Planning provider unavailable` locally**: Ollama isn't running. Run `brew services restart ollama` or `ollama serve` in a terminal.
- **`LLM returned invalid JSON`**: the local model (llama3.2) sometimes emits code fences or extra text; the service strips fences, but occasionally it hallucinates. Regenerate. Gemini in prod is much more disciplined about JSON mode.
- **Prod 500 with `RESOURCE_EXHAUSTED`**: you hit Gemini's free-tier RPM (10/min) or daily cap (250/day). Wait a minute and retry. Planning is 1 request per generate so you have a lot of headroom.
- **Prod 401 `Please pass a valid API key`**: `PLANNING_API_KEY` isn't wired. Redo step 3 + 4 and confirm the revision env with `gcloud run revisions describe ... --format="yaml(spec.containers[0].env)"`.
- **Local plan takes forever**: llama3.2 is slow on CPU. Optional: `ollama pull qwen2.5-coder:7b` (better at code, similar speed) and set `PLANNING_MODEL=qwen2.5-coder:7b` in `.env`.

## What's next

Week 7: **Migration agents.** We give Gemini a single file plus its plan entry, and ask it to actually produce the modernised Spring Boot equivalent — a real generated `.java` file that compiles. We run each file's migration in parallel through an agent orchestrator, track successes and failures per phase, and store the generated code alongside the original for side-by-side diff review in the UI.
