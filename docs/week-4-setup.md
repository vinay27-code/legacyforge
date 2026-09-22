# Week 4: Analysis setup

Goal: parse every `.java` file in an ingested repo, detect frameworks, compute per-file complexity, and show it all in an Analysis tab on the repo detail page.

## 1. Extract the zip

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week4.zip -d /tmp/lf-w4
cp -R /tmp/lf-w4/legacyforge-w4/backend/. backend/
cp -R /tmp/lf-w4/legacyforge-w4/frontend/. frontend/
cp -R /tmp/lf-w4/legacyforge-w4/docs/. docs/
rm -rf /tmp/lf-w4
```

## 2. Run locally

```bash
cd ~/dev/legacyforge
set -a && source .env && set +a
cd backend
mvn clean spring-boot:run
```

Watch for `Successfully applied 1 migration to schema "public", now at version v5`.

New terminal:

```bash
cd ~/dev/legacyforge/frontend
npm start
```

Open http://localhost:4200, log in as admin, click an existing repo (or ingest jpetstore-6 fresh).

## 3. Test the analysis flow

1. On the repo detail page, click the **Analysis** tab
2. Click **Run analysis** (takes 2-5 seconds for a small repo)
3. You should see:
   - **KPI row:** Java files count, LOC, avg/max complexity
   - **Detected frameworks:** e.g. "MyBatis 3.5", "JSP", "Spring Boot" with confidence bars
   - **Languages:** breakdown by file count and bytes
   - **Findings:** roll-up of migration smells (raw JDBC, @Autowired field injection, System.out, etc.)
   - **Most complex files:** top 20 files sorted by cyclomatic complexity

For jpetstore-6 you should see frameworks like MyBatis, JSP, Spring Boot, EJB (if imports match).

## 4. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git status
git commit -m "Week 4: AST parsing, framework fingerprinting, complexity scoring"
git push
gh run watch
```

Flyway V5 runs on the next Cloud Run boot. Test in prod after Vercel + Cloud Run finish.

## API smoke tests

```bash
# Log in first
TOKEN=$(curl -s -c /tmp/lf.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@legacyforge.dev","password":"admin12345"}' | jq -r .accessToken)

# Get first repo id
REPO_ID=$(curl -sH "Authorization: Bearer $TOKEN" http://localhost:8080/api/repos | jq -r '.[0].id')

# Run analysis
curl -H "Authorization: Bearer $TOKEN" -X POST \
  http://localhost:8080/api/repos/$REPO_ID/analysis | jq

# Get top complex files
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/repos/$REPO_ID/analysis/top-complex?limit=10 | jq
```

## Common issues

- **`org.hibernate.MappingException: No Dialect mapping for JDBC type: JSONB`:** old Hibernate. Spring Boot 3.3 handles it natively with `@JdbcTypeCode(SqlTypes.JSON)`. If you see this, upgrade Spring Boot version.
- **`JavaParser Exception`:** the parser is lenient (Java 21 grammar accepts Java 8), but some files might have syntax errors from the source repo. We swallow parse errors per file to avoid killing the whole analysis.
- **"Not analyzed yet" persists after running:** the run threw. Backend logs will have the reason.

## What's next

Week 5: RAG pipeline. We chunk every file, generate embeddings, store them in pgvector, and build a semantic retrieval endpoint. That's the foundation the AI agents will use in Week 7-8 to migrate code with real context.
