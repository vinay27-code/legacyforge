# Week 1: Foundation setup

Goal: Spring Boot 3 backend + Angular 18 frontend running locally, wired end to end, deployed to Cloud Run and Vercel. By the end of the week, clicking a button on your live Vercel URL calls your live Cloud Run URL and shows a JSON response.

## Prerequisites

Install these once:

- Java 21: `sdk install java 21.0.4-tem` (via SDKMAN) or download from Adoptium
- Node 20: `nvm install 20 && nvm use 20`
- Docker Desktop
- Git
- (Optional) `gh` CLI for GitHub, `gcloud` CLI for Cloud Run

## Step 1: Extract the scaffolding into your repo

Unzip `legacyforge-week1.zip` into your local `legacyforge/` folder. Your tree should now look like this:

```
legacyforge/
├── .env.example
├── .github/workflows/ci-cd.yml
├── .gitignore
├── README.md
├── docker-compose.yml
├── docs/
│   ├── architecture.md
│   └── week-1-setup.md
├── scripts/init-db.sql
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/...
└── frontend/
    ├── Dockerfile
    ├── package.json
    ├── angular.json
    ├── nginx.conf
    ├── vercel.json
    └── src/...
```

Copy `.env.example` to `.env` and fill in what you have. You do NOT need Groq or Gemini keys yet, those come in Week 5.

## Step 2: Start local infrastructure

```bash
docker compose up -d
```

Verify:
- Postgres: `docker exec -it legacyforge_postgres psql -U legacyforge -d legacyforge -c "SELECT extname FROM pg_extension;"` should show `vector`.
- Redis: `docker exec -it legacyforge_redis redis-cli ping` should return `PONG`.
- pgAdmin: open http://localhost:5050 and log in with `dev@legacyforge.local` / `admin`.

## Step 3: Run the backend

```bash
cd backend
./mvnw spring-boot:run
# or, if no wrapper yet:
mvn spring-boot:run
```

Wait for `Started LegacyForgeApplication in X seconds`.

Test:
```bash
curl http://localhost:8080/api/hello
curl http://localhost:8080/actuator/health
```

You should see JSON responses.

## Step 4: Run the frontend

In another terminal:

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200. Click the "Ping backend" button. You should see the JSON response from the backend rendered in green.

If you see a CORS error in the browser console, check that `app.cors.allowed-origins` in `backend/src/main/resources/application.yml` includes `http://localhost:4200`.

## Step 5: Push to GitHub

```bash
cd /path/to/legacyforge
git add .
git commit -m "Week 1: initial scaffolding for backend, frontend, docker, CI"
git push origin main
```

GitHub Actions will run `backend-test` and `frontend-test`. Both should pass green. The build/deploy jobs will skip until you add the secrets in Step 6.

## Step 6: Set up cloud accounts (do these one at a time)

### 6a. Supabase (Postgres + pgvector)

1. https://supabase.com, sign up with GitHub, create a new project (free tier).
2. In the SQL editor, run:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```
3. Grab the DB connection string from Project Settings → Database → Connection string → "URI".
4. In Project Settings → Database → Connection Pooler, copy the Transaction Pooler URL for production use.

### 6b. Upstash Redis

1. https://upstash.com, sign up with GitHub.
2. Create a Redis database in region `us-east-1`, TLS enabled.
3. Copy the Redis URL and password.

### 6c. Google Cloud Run

1. https://console.cloud.google.com, create a new project called `legacyforge`.
2. Enable the Cloud Run API and Artifact Registry API (or skip Artifact Registry, we use GHCR).
3. Enable Secret Manager API.
4. Create a service account with roles: `Cloud Run Admin`, `Service Account User`, `Secret Manager Secret Accessor`.
5. Create a JSON key for that service account, download it.
6. Create secrets in Secret Manager: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `GROQ_API_KEY` (put placeholders for keys you don't have yet).

### 6d. Vercel

1. https://vercel.com, sign up with GitHub.
2. Import the `legacyforge` repo.
3. **Root directory:** `frontend`
4. **Framework preset:** Angular
5. **Build command:** `npm run build`
6. **Output directory:** `dist/legacyforge/browser`
7. Deploy. Copy the generated URL (e.g. `https://legacyforge-vinay27code.vercel.app`).

## Step 7: Add GitHub secrets

In `github.com/vinay27-code/legacyforge/settings/secrets/actions`, add:

| Secret | Value |
| :--- | :--- |
| `GCP_SA_KEY` | Contents of the service account JSON from 6c |
| `GCP_PROJECT_ID` | Your GCP project ID |
| `FRONTEND_ORIGIN` | Your Vercel URL from 6d |

## Step 8: Trigger a deploy

Push a small change to `main`:

```bash
git commit --allow-empty -m "Trigger first deploy"
git push
```

Watch the Actions tab. `backend-test` runs, then `backend-build` pushes a Docker image to GHCR, then `backend-deploy` deploys to Cloud Run. Grab the Cloud Run URL from the workflow logs (or the GCP console).

## Step 9: Wire the frontend to the deployed backend

1. Edit `frontend/src/environments/environment.prod.ts` and paste your Cloud Run URL as `apiBaseUrl`.
2. Commit and push. Vercel auto redeploys.

## Step 10: End to end smoke test

Open your Vercel URL. Click "Ping backend". You should see the JSON response with a fresh timestamp from Cloud Run.

**If this works, Week 1 is done.**

## Common issues

- **Backend fails to start with a Postgres connection error:** Docker Postgres isn't running, or you edited the connection URL. Run `docker compose up -d` again.
- **Angular `npm install` fails on some peer dep:** try `npm install --legacy-peer-deps`.
- **CORS blocked in browser:** the backend's `FRONTEND_ORIGIN` env var doesn't include your Vercel URL. Update the GitHub secret and re-deploy.
- **Cloud Run deploy fails with `PERMISSION_DENIED`:** the service account is missing a role. Add `Cloud Run Admin` and `Service Account User`.
- **Flyway migration fails on Supabase:** you didn't enable the `vector` extension. Run the SQL from 6a.

## Deliverable checklist

- [ ] `docker compose up -d` gives you Postgres + Redis + pgAdmin
- [ ] Backend runs locally on http://localhost:8080
- [ ] `GET /api/hello` returns JSON
- [ ] Frontend runs locally on http://localhost:4200
- [ ] Ping button on the frontend returns the JSON from the backend
- [ ] Repo is pushed to `github.com/vinay27-code/legacyforge`
- [ ] Backend is deployed to Cloud Run at a public URL
- [ ] Frontend is deployed to Vercel at a public URL
- [ ] Live Vercel URL can ping the live Cloud Run URL

## Next: Week 2 preview

Once this all works, Week 2 is:
- Spring Security 6 + JWT auth (access + refresh tokens)
- Register + login endpoints
- Angular auth service, HTTP interceptor, route guards
- Angular Material shell (sidenav + toolbar)
- Empty dashboard page after login
