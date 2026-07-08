# Deployment Guide

Production topology: **Frontend → Vercel**, **Backend + AI Service → Render**,
**Database → any managed MySQL** (Render, PlanetScale, AWS RDS, etc).

## 1. Database

Provision a MySQL 8 instance and run the scripts in `database/` against it:

```bash
mysql -h <host> -u <user> -p < database/schema.sql
mysql -h <host> -u <user> -p < database/seed.sql
```

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

## 5. Post-Deployment Checklist

- [ ] Log in with the seeded admin account and **change its password immediately**
- [ ] Rotate `JWT_SECRET` to a value generated specifically for production
- [ ] Restrict `CORS_ALLOWED_ORIGINS` to only your real frontend domain
- [ ] Configure SMTP credentials if critical email alerts are required
- [ ] Confirm `/api/v1/threat-intel/*` and `/api/v1/assistant/chat` respond correctly
      end-to-end through the deployed frontend

## Local Docker Compose (staging/dev parity)

```bash
docker compose up --build
```

Spins up MySQL, the AI service, the backend, and an Nginx-served frontend build,
wired together with the same environment variable contracts used in production.
