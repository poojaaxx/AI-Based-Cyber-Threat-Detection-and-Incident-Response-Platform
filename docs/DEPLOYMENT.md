# Deployment Guide

Production topology: **Frontend → Vercel**, **Backend + AI Service → Render**,
**Database → any managed MySQL** (Render, PlanetScale, AWS RDS, etc).

## 1. Database

Provision a MySQL 8 instance. On a **fresh** database, run the base schema, then every
migration in `database/migrations/` in filename order, then the seed data:

```bash
mysql -h <host> -u <user> -p < database/schema.sql
mysql -h <host> -u <user> -p < database/migrations/20260921_stabilization.sql
mysql -h <host> -u <user> -p < database/migrations/20260922_authentication_monitoring.sql
mysql -h <host> -u <user> -p < database/migrations/20260922_network_monitoring.sql
mysql -h <host> -u <user> -p < database/seed.sql
```

On an **existing** production database, back it up first (e.g.
`mysqldump -h <host> -u <user> -p <db> > backup.sql`), then apply only the migration
files you haven't already run — each one is written to be safe to check for
(they guard column/table additions with `information_schema` existence checks). Do not
re-run `schema.sql` against a database that already has data; it is for a fresh
initial DB only. Confirm afterward that `users.locked_until`, `incidents.reported_by`
(nullable), `security_events`, `network_events.collector_id`/`session_id`/`sequence_number`,
and `collector_state` all exist as expected.

## 2. AI Service (Render)

1. Create a new **Web Service** on Render, pointing at `ai-service/Dockerfile`
   (or use the `deployment/render.yaml` Blueprint to provision both backend and
   AI service in one step).
2. No environment variables are required beyond `CORS_ALLOWED_ORIGINS`
   (comma-separated list including your Vercel frontend URL and Render backend URL).
3. The Docker image trains the model at build time (`RUN python -m app.ml.train_model`),
   so the service is ready to serve predictions immediately on first boot.
4. Note the deployed URL, e.g. `https://cyberguard-ai-service.onrender.com`.

## 3. Backend (Render)

1. Create a second **Web Service** pointing at `backend/Dockerfile`.
2. Set environment variables:
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — your managed MySQL connection details
   - `JWT_SECRET` — a strong, unique Base64 secret (never reuse the dev default)
   - `AI_SERVICE_URL` — the AI service URL from step 2
   - `CORS_ALLOWED_ORIGINS` — your Vercel frontend URL
   - `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` — optional, for critical alert emails
3. Render's health check should point at `/actuator/health` (add the actuator
   dependency or a simple `/health` controller if you disable it).
4. Note the deployed URL, e.g. `https://cyberguard-backend.onrender.com`.

## 4. Frontend (Vercel)

1. Import the repository into Vercel, setting **Root Directory** to `frontend`.
2. Vercel auto-detects the Vite framework via `frontend/vercel.json`.
3. Set the environment variable `VITE_API_BASE_URL` to
   `https://cyberguard-backend.onrender.com/api/v1`.
4. Deploy. Vercel will build with `npm run build` and serve the `dist/` output,
   with SPA rewrites already configured so client-side routing works on refresh.

## 5. Remote Windows Collector (Phase 3B, optional)

By default the collector-ingestion endpoint only accepts loopback requests, matching the
verified local Phase 3B demo. To let a collector running on a Windows laptop send real TCP
telemetry to the **deployed** backend over the Internet, set on the backend service:

- `COLLECTOR_ENABLED=true`
- `COLLECTOR_REMOTE_ENABLED=true`
- `COLLECTOR_INGEST_KEY` — a dedicated, high-entropy secret (32+ chars). Never reuse a user
  password, admin JWT, or browser token. `deployment/render.yaml` auto-generates this.
- `COLLECTOR_MAX_CLOCK_SKEW_SECONDS` (default `300`) and `COLLECTOR_RATE_LIMIT_PER_MINUTE`
  (default `300`) — tune only if you have a reason to.

Loopback-only local demos keep working unchanged regardless of these settings — remote access
is strictly additive. See [`collectors/windows/README.md`](../collectors/windows/README.md)
for the exact production command, how the key is transmitted, and the security model.

## 6. Post-Deployment Checklist

- [ ] Log in with the seeded admin account and **change its password immediately**
- [ ] Rotate `JWT_SECRET` to a value generated specifically for production
- [ ] Restrict `CORS_ALLOWED_ORIGINS` to only your real frontend domain
- [ ] Configure SMTP credentials if critical email alerts are required
- [ ] If using the remote collector, confirm `COLLECTOR_INGEST_KEY` is a dedicated
      generated secret and was never printed to logs, chat, or committed to git
- [ ] Confirm `/api/v1/threat-intel/*` and `/api/v1/assistant/chat` respond correctly
      end-to-end through the deployed frontend

## Local Docker Compose (staging/dev parity)

```bash
docker compose up --build
```

Spins up MySQL, the AI service, the backend, and an Nginx-served frontend build,
wired together with the same environment variable contracts used in production.
