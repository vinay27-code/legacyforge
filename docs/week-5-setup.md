# Week 5: RAG setup

Goal: chunk every file, embed each chunk, store in pgvector, expose a semantic search endpoint. Local dev uses **Ollama** with `nomic-embed-text`. Prod uses **Gemini** `text-embedding-004`. Both output 768-dim vectors, same pgvector schema.

## 1. Extract the zip

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week5.zip -d /tmp/lf-w5
cp -R /tmp/lf-w5/legacyforge-w5/backend/. backend/
cp -R /tmp/lf-w5/legacyforge-w5/frontend/. frontend/
cp -R /tmp/lf-w5/legacyforge-w5/docs/. docs/
rm -rf /tmp/lf-w5
```

## 2. Install Ollama (local dev)

```bash
brew install ollama
brew services start ollama
# or run in foreground: ollama serve
```

Verify it's up:

```bash
curl http://localhost:11434/api/tags
```

Should return `{"models":[]}` on first install.

Pull the embedding model (about 300 MB, downloads in 30-60 seconds):

```bash
ollama pull nomic-embed-text
```

Test it:

```bash
curl http://localhost:11434/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","input":"public class Foo {}"}' \
  | head -c 200
```

Should print `{"data":[{"embedding":[0.02...` (a big vector).

## 3. Add the Gemini API key for prod

Add it to your GitHub secrets so Cloud Run will get it on the next deploy:

```bash
gh secret set GEMINI_API_KEY --repo vinay27-code/legacyforge --body "<your-gemini-api-key>"
```

And add it as a GCP Secret Manager entry that Cloud Run will read at runtime:

```bash
echo -n "<your-gemini-api-key>" | gcloud secrets create GEMINI_API_KEY \
  --data-file=- --project=$GCP_PROJECT_ID
```

(If you already created `GEMINI_API_KEY` earlier as a placeholder, add a new version instead:)

```bash
echo -n "<your-gemini-api-key>" | gcloud secrets versions add GEMINI_API_KEY \
  --data-file=- --project=$GCP_PROJECT_ID
```

## 4. Update the CI workflow to wire that secret into Cloud Run

Open `.github/workflows/ci-cd.yml`. Find the `--set-secrets` line in the deploy step:

```yaml
--set-secrets "DB_URL=DB_URL:latest,DB_USERNAME=DB_USERNAME:latest,DB_PASSWORD=DB_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest,GROQ_API_KEY=GROQ_API_KEY:latest"
```

Add `EMBEDDING_API_KEY=GEMINI_API_KEY:latest` to the end (the `EMBEDDING_API_KEY` env var maps to the `GEMINI_API_KEY` secret). Result:

```yaml
--set-secrets "DB_URL=DB_URL:latest,DB_USERNAME=DB_USERNAME:latest,DB_PASSWORD=DB_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest,GROQ_API_KEY=GROQ_API_KEY:latest,EMBEDDING_API_KEY=GEMINI_API_KEY:latest"
```

## 5. Run backend + frontend locally

```bash
cd ~/dev/legacyforge
set -a && source .env && set +a
cd backend
mvn clean spring-boot:run
```

Watch for `Successfully applied 1 migration to schema "public", now at version v6` and `EmbeddingProvider: base=http://localhost:11434/v1 model=nomic-embed-text`.

Another terminal:

```bash
cd ~/dev/legacyforge/frontend
npm start
```

Open http://localhost:4200. Log in as admin, click your jpetstore repo. You should see a **Semantic search** tab.

## 6. Test the flow

1. Click **Semantic search** tab
2. Click **Build search index** — takes 10-30 seconds for jpetstore-6 (about 300 chunks × Ollama at ~50-100ms each)
3. Once indexed, the search bar appears. Try queries like:
   - `where is the user login logic?`
   - `how does the cart calculate totals?`
   - `product catalog CRUD operations`
   - `iBATIS SQL mappings for orders`
4. Should return the top-10 most similar chunks with % match, chunk type, and content preview.

## 7. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git status
git commit -m "Week 5: RAG pipeline (chunking + embeddings + pgvector search)"
git push
gh run watch
```

Wait for all four jobs green. Cloud Run redeploys with V6 migration applied. First analysis in prod will use Gemini `text-embedding-004`.

## Common issues

- **`Embedding provider unavailable`** locally: Ollama isn't running. Run `brew services restart ollama` or `ollama serve` in a terminal.
- **`Embedding count mismatch`**: batch API returned fewer vectors than we sent inputs. Usually a transient issue with the provider; rebuild the index.
- **`missing dimensions`** in pgvector: your model outputs a different number of dimensions than 768. Check `curl http://localhost:11434/v1/embeddings ...` and count the array length in the response. If it's not 768, adjust the `vector(768)` in `V6__code_chunks.sql` and drop/recreate the table.
- **Prod search is slow**: first request cold-starts Cloud Run + Gemini. Second request should be < 1s.
- **Prod indexing fails with 401 from Gemini**: `GEMINI_API_KEY` isn't wired into Cloud Run. Redo step 3 + 4.

## What's next

Week 6: LLM-driven migration planning. We feed the analysis + framework fingerprint from Week 4 to Gemini 2.5 Flash (huge context window fits the whole codebase) and get back a phased migration plan with risk scoring per file. That's what unlocks Week 7-8 (the migration agents).
