# Week 2: Auth setup

Goal: full JWT auth with httpOnly refresh cookies, register/login/logout endpoints, and a protected dashboard.

## What you'll ship

- Backend: Spring Security + JWT + refresh tokens, endpoints `/api/auth/register|login|refresh|logout|whoami`
- Frontend: Login and register pages, Angular Material shell, dashboard protected by an auth guard
- Seeded admin: `admin@legacyforge.dev` / `admin12345`

## 1. Extract the zip

Unzip `legacyforge-week2.zip` into a temp folder, then merge into your repo:

```bash
cd ~/dev/legacyforge
unzip -o ~/Downloads/legacyforge-week2.zip -d /tmp/lf-w2
cp -R /tmp/lf-w2/backend/. backend/
cp -R /tmp/lf-w2/frontend/. frontend/
cp -R /tmp/lf-w2/docs/. docs/
rm -rf /tmp/lf-w2
```

## 2. Delete the old CorsConfig (SecurityConfig owns CORS now)

```bash
rm -f backend/src/main/java/com/legacyforge/config/CorsConfig.java
```

## 3. Install new frontend deps

`@angular/material` was already in Week 1's `package.json`, so this should be a no-op, but run it to be safe:

```bash
cd frontend
npm install
cd ..
```

## 4. Run locally

Start containers if they're not running:

```bash
docker compose up -d
```

Then boot the backend. From this week on, keep sourcing your `.env` so envs like `DB_URL` (with port 5433) get set:

```bash
cd ~/dev/legacyforge
set -a && source .env && set +a
cd backend
mvn clean spring-boot:run
```

Watch for:
- `Seeded ADMIN user: admin@legacyforge.dev`
- `Started LegacyForgeApplication`

In another terminal:

```bash
cd ~/dev/legacyforge/frontend
npm start
```

Open http://localhost:4200. You should get the login page.

Log in as:
- Email: `admin@legacyforge.dev`
- Password: `admin12345`

You should land on the dashboard, see your email + `ADMIN` chip in the header, click Ping backend, and get green JSON.

## 5. Smoke test the endpoints via curl

```bash
# Register a new user
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -c /tmp/lf-cookie.txt \
  -d '{"email":"you@example.com","password":"password12345"}'

# Login as admin
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c /tmp/lf-cookie.txt \
  -d '{"email":"admin@legacyforge.dev","password":"admin12345"}'
# Copy the accessToken from the response.

# whoami (replace TOKEN)
curl -H "Authorization: Bearer TOKEN" http://localhost:8080/api/auth/whoami

# Refresh (uses cookie)
curl -i -X POST http://localhost:8080/api/auth/refresh \
  -b /tmp/lf-cookie.txt -c /tmp/lf-cookie.txt

# Logout
curl -i -X POST http://localhost:8080/api/auth/logout \
  -b /tmp/lf-cookie.txt -c /tmp/lf-cookie.txt
```

## 6. Push to prod

```bash
cd ~/dev/legacyforge
git add .
git status  # confirm no secrets or .env leaked
git commit -m "Week 2: JWT auth + refresh cookies + Angular Material shell"
git push
gh run watch
```

Wait for all four jobs green. First deploy after this will restart Cloud Run with the new schema; Flyway auto-runs. The admin gets seeded on the first prod boot too (via the same `ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars, which use their defaults if unset).

## 7. Test in prod

Open your Vercel URL. Log in as `admin@legacyforge.dev` / `admin12345`. Click Ping backend. All green.

**Change the admin password ASAP** in production:
1. Register your own account through the UI
2. Bump that account to ADMIN via a quick SQL: `UPDATE users SET role='ADMIN' WHERE email='you@example.com';`
3. Delete the seeded admin: `DELETE FROM users WHERE email='admin@legacyforge.dev';`
4. Do this via Supabase's SQL editor.

## Common issues

- **CORS error in the browser:** the backend's `FRONTEND_ORIGIN` doesn't include your current Vercel URL. Update the GitHub secret.
- **Cookie not being sent:** the frontend must call with `withCredentials: true` (the interceptor does this automatically). If you added a direct http call somewhere, add `{ withCredentials: true }`.
- **Login works but page reload logs you out:** the httpOnly cookie is missing (probably CORS blocked it). Check the browser Network tab for the login response — you should see a `Set-Cookie: lf_rt=...` header. If the cookie is present but not sent on the next request, verify `SameSite=None; Secure` is being set (only works over HTTPS in prod, or Chrome will silently drop it).
- **Backend fails to start with schema validation error:** the Flyway migration didn't run. Check `flyway_schema_history` table.

## What's next

Week 3: repository ingestion. Upload a legacy Java zip or paste a GitHub URL, backend clones it, stores it in Supabase Storage, and shows the file tree in the UI.
