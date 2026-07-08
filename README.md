# CyberGuard — AI-Based Cyber Threat Detection and Incident Response Platform

An enterprise-grade Security Operations Center (SOC) platform that combines real-time
monitoring, AI-based threat detection, threat intelligence, automated incident response,
and an AI security assistant into a single, centralized dashboard.

This project extends common AI-driven network intrusion detection research into a
practical, full-stack platform suitable for a final-year engineering capstone or a
small-organization SOC deployment.

## Architecture

```
                         ┌─────────────────────┐
                         │   React Frontend     │  (Vite, Tailwind, Recharts)
                         │   SOC Dashboard       │
                         └──────────┬───────────┘
                                    │ REST + JWT
                         ┌──────────▼───────────┐
                         │   Spring Boot API     │  (Auth, Incidents, Threats,
                         │   (Backend)           │   Reports, Audit, Notifications)
                         └───────┬───────┬───────┘
                                 │       │
                     JDBC/JPA    │       │  REST (WebClient)
                         ┌───────▼┐   ┌──▼─────────────────┐
                         │  MySQL │   │  FastAPI AI Service  │
                         │        │   │  (RandomForest model  │
                         │        │   │   + AI Assistant)     │
                         └────────┘   └───────────────────────┘
```

## Tech Stack

| Layer       | Technology                                              |
|-------------|----------------------------------------------------------|
| Frontend    | React (Vite), React Router, Axios, Tailwind CSS, Recharts |
| Backend     | Spring Boot 3, Spring Security, Spring Data JPA, JWT, Maven |
| Database    | MySQL 8                                                   |
| AI Service  | Python, FastAPI, scikit-learn, Pandas, NumPy              |
| Deployment  | Docker, Vercel (frontend), Render (backend + AI service)   |

## Modules

1. **Authentication** — JWT access/refresh tokens, RBAC (Admin / Security Analyst / User)
2. **Dashboard** — security score, active threats, statistics, trends, recent activity
3. **Real-Time Monitoring** — system logs, login attempts, network events
4. **AI Threat Detection** — 9-class RandomForest classifier (malware, DDoS, SQL injection,
   XSS, brute force, port scan, phishing, ransomware, insider threat)
5. **Threat Classification** — Low / Medium / High / Critical severity
6. **Threat Intelligence** — CVE database, IOC feed, MITRE ATT&CK mapping
7. **Automated Incident Response** — block IP, disable user, quarantine threat, notify admin
8. **AI Security Assistant** — chat-based threat/CVE explanations and mitigation guidance
9. **Incident Management** — create, assign, update, resolve, timeline, comments
10. **Reports** — PDF, CSV, Excel export
11. **Notifications** — dashboard + email alerts for critical threats
12. **Visual Analytics** — line/pie/bar charts, threat & incident trends
13. **Audit Logs** — full action trail
14. **Settings** — profile, password, notification preferences, API configuration

## Repository Layout

```
frontend/     React SOC dashboard
backend/      Spring Boot REST API
ai-service/   FastAPI ML threat detection + AI assistant
database/     MySQL schema.sql + seed.sql
docs/         API docs, installation & deployment guides
deployment/   Render/Vercel deployment configs
docker-compose.yml
```

## Quick Start

See [docs/INSTALLATION.md](docs/INSTALLATION.md) for full setup instructions, or run
everything with Docker:

```bash
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080 (Swagger UI at `/swagger-ui.html`)
- AI Service: http://localhost:8000 (interactive docs at `/docs`)

Default seeded login: **admin / Password@123**

## Documentation

- [Installation Guide](docs/INSTALLATION.md)
- [API Documentation](docs/API_DOCUMENTATION.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
