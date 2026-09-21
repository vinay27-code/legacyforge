# Week 3: Ingestion setup

Goal: users can upload a legacy Java zip or paste a public GitHub URL. The backend clones or extracts it, indexes every file into Postgres, and the frontend shows the file tree with a click-to-view file browser.

## 1. Extract the zip

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week3.zip -d /tmp/lf-w3
cp -R /tmp/lf-w3/legacyforge-w3/backend/. backend/
cp -R /tmp/lf-w3/legacyforge-w3/frontend/. frontend/
cp -R /tmp/lf-w3/legacyforge-w3/docs/. docs/
rm -rf /tmp/lf-w3
```

## 2. Local test

Docker containers should already be running. Start backend:

```bash
cd ~/dev/legacyforge
set -a && source .env && set +a
cd backend
mvn clean spring-boot:run
```

Watch for `Flyway ... successfully applied 1 migration to schema "public", now at version v3` and `Started LegacyForgeApplication`.

New terminal, start frontend:

```bash
cd ~/dev/legacyforge/frontend
npm install
npm start
```

Open http://localhost:4200. Log in as `admin@legacyforge.dev` / `admin12345`.

## 3. First ingest

Click **Migrations** in the sidebar, then **New migration**.

**Option A: paste a GitHub URL.** Good legacy Java 8 / JSP candidates:
- `https://github.com/mybatis/jpetstore-6` (Java + JSP + Spring MVC 4 — the canonical legacy sample)
- `https://github.com/spring-projects/spring-petclinic` (small, well known)
- `https://github.com/apache/struts-examples` (Struts, if you want to test something older)

Paste one, hit Ingest. Takes 5-15 seconds. Lands on the repo detail page.

**Option B: upload a zip.** Download any repo as zip from GitHub ("Code → Download ZIP"), drag it onto the drop zone, hit Upload.

## 4. Explore the file tree

- Click folders to expand
- Click a file to view its contents in the pane on the right
- File tree shows file names, sizes, and detected language
- Binaries and files > 1 MB show "Preview unavailable"

## 5. Verify DB

```bash
docker exec legacyforge_postgres psql -U legacyforge -d legacyforge \
  -c "SELECT name, source_type, file_count, total_size_bytes, status FROM repos;"

docker exec legacyforge_postgres psql -U legacyforge -d legacyforge \
  -c "SELECT language, COUNT(*) FROM repo_files GROUP BY language ORDER BY 2 DESC LIMIT 10;"
```

You should see the repo row and a language breakdown (java, jsp, xml, properties, etc).

## 6. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git status  # confirm no .env or secrets
git commit -m "Week 3: repo ingestion (zip upload + GitHub URL) with file tree UI"
git push
gh run watch
```

Wait for all four jobs green. Once Cloud Run redeploys, Flyway applies V3 migration. Test the flow at your Vercel URL just like locally.

## API smoke tests

```bash
# Log in first, save cookie:
curl -c /tmp/lf.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@legacyforge.dev","password":"admin12345"}' | jq -r .accessToken > /tmp/lf.token

TOKEN=$(cat /tmp/lf.token)

# Ingest from GitHub
curl -b /tmp/lf.txt -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/repos/github \
  -d '{"url":"https://github.com/mybatis/jpetstore-6"}' | jq

# List repos
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/repos | jq

# Get the tree of the first repo
REPO_ID=$(curl -sH "Authorization: Bearer $TOKEN" http://localhost:8080/api/repos | jq -r '.[0].id')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/repos/$REPO_ID/tree | jq | head -50

# Read one specific file
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/repos/$REPO_ID/file?path=pom.xml" | jq
```

## Common issues

- **413 on zip upload:** file over 100 MB. Bump `spring.servlet.multipart.max-file-size` in application.yml.
- **GitHub clone fails:** the URL you pasted isn't a valid public repo, or JGit couldn't reach github.com. Cloud Run has open egress so this should work in prod.
- **File tree empty:** something in ingestion threw. Check `SELECT status, error_message FROM repos;` — a `FAILED` row means the ingest crashed. Backend logs will have the stack trace.
- **Repo shows READY but 0 files:** everything was filtered out as ignored dirs (like a repo that only contains `.git/` after some weird zip). Try a different sample.

## Week 3 accomplishments

- Repo table + files table with SHA-256, language, binary flags
- Zip extraction with zip-bomb + path-traversal defenses
- JGit shallow clone for GitHub repos
- Extension-based language detector for 40+ languages
- Recursive Angular file tree component
- Drag-drop upload with progress bar
- Multi-tab wizard (GitHub URL vs zip)

## What's next

Week 4: AST parsing with JavaParser, framework fingerprinting (Struts, Spring MVC, JSP, JSF), dependency graph, complexity scoring. That builds on this week's file storage — every ingested repo becomes fair game for analysis.
