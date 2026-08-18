<div align="center">

![Typing SVG](https://readme-typing-svg.demolab.com/?font=Fira+Code&size=22&pause=1000&color=2FD3D3&center=true&vCenter=true&width=600&lines=AI-Powered+Threat+Detection;Automated+Incident+Response;Real-Time+Security+Monitoring)

# 🛡️ CyberGuard
### AI-Based Cyber Threat Detection & Incident Response Platform

*An enterprise-grade Security Operations Center (SOC) platform combining real-time monitoring,*
*AI-powered threat detection, threat intelligence, automated incident response, and an AI security assistant*
*into a single centralized dashboard.*

[![GitHub last commit](https://img.shields.io/github/last-commit/poojaaxx/AI-Based-Cyber-Threat-Detection-and-Incident-Response-Platform?style=flat-square)](https://github.com/poojaaxx/AI-Based-Cyber-Threat-Detection-and-Incident-Response-Platform/commits/main)
[![GitHub repo size](https://img.shields.io/github/repo-size/poojaaxx/AI-Based-Cyber-Threat-Detection-and-Incident-Response-Platform?style=flat-square)](https://github.com/poojaaxx/AI-Based-Cyber-Threat-Detection-and-Incident-Response-Platform)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](#-license)

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](#)
[![React](https://img.shields.io/badge/React-18.3-61DAFB?style=flat-square&logo=react&logoColor=black)](#)
[![Vite](https://img.shields.io/badge/Vite-5.3-646CFF?style=flat-square&logo=vite&logoColor=white)](#)
[![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=flat-square&logo=python&logoColor=white)](#)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.111-009688?style=flat-square&logo=fastapi&logoColor=white)](#)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)](#)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?style=flat-square&logo=docker&logoColor=white)](#)
[![Render](https://img.shields.io/badge/Render-Deployed-46E3B7?style=flat-square&logo=render&logoColor=white)](#)
[![Vercel](https://img.shields.io/badge/Vercel-Deployed-000000?style=flat-square&logo=vercel&logoColor=white)](#)
[![PyTorch](https://img.shields.io/badge/PyTorch-2.13-EE4C2C?style=flat-square&logo=pytorch&logoColor=white)](#)
[![SHAP](https://img.shields.io/badge/Explainability-SHAP-8A2BE2?style=flat-square)](#)

<!--
  🎬 Demo video / GIF placeholder.
  Record a short walkthrough (login → dashboard → threat simulation → AI assistant)
  and drop it in docs/media/demo.gif, then uncomment the line below.

  ![CyberGuard demo](docs/media/demo.gif)
-->

</div>

---

## 🚀 Live Demo

<div align="center">

![Status](https://img.shields.io/badge/🟢-Live-brightgreen?style=flat-square)

[![Frontend](https://img.shields.io/badge/🌐_Frontend-Live_App-1a73e8?style=for-the-badge)](https://ai-based-cyber-threat-detection-and.vercel.app)
[![Backend API](https://img.shields.io/badge/⚙️_Backend_API-Swagger_UI-6DB33F?style=for-the-badge)](https://ai-based-cyber-threat-detection-and.onrender.com/swagger-ui.html)
[![AI Service](https://img.shields.io/badge/🤖_AI_Service-Docs-009688?style=for-the-badge)](https://cyberguard-ai-service-84vt.onrender.com/docs)
[![Repository](https://img.shields.io/badge/📂_Source-GitHub-181717?style=for-the-badge&logo=github)](https://github.com/poojaaxx/AI-Based-Cyber-Threat-Detection-and-Incident-Response-Platform)

**Demo login:** `admin` / `Password@123`

</div>

> ⚠️ Backend and AI service run on Render's free tier, which spins down after ~15 minutes of
> inactivity. The **first** request after idle time may take 30–60 seconds to wake up — this is
> expected, not a bug.

---

## 📑 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Screenshots](#-screenshots)
- [Installation](#-installation)
- [Environment Variables](#-environment-variables)
- [API Documentation](#-api-documentation)
- [Deployment](#-deployment)
- [Project Highlights](#-project-highlights)
- [Future Enhancements](#-future-enhancements)
- [License](#-license)

---

## ✨ Features

| | | |
|---|---|---|
| 🧠 **AI-Powered Threat Detection** | 🚨 **Automated Incident Response** | 🔐 **JWT Authentication** |
| 👥 **Role-Based Access Control** | 🕵️ **Threat Intelligence** (CVE / IOC / MITRE ATT&CK) | 📊 **Security Dashboard** |
| 📄 **Reports** (PDF / CSV / Excel) | 🔔 **Real-Time Notifications** | 💬 **AI Security Assistant** |
| 📈 **Visual Analytics** | 🐳 **Docker Deployment** | ☁️ **Cloud Deployment** |

<details>
<summary><strong>📋 Full module breakdown</strong> (click to expand)</summary>

1. **Authentication** — JWT access/refresh tokens, RBAC (Admin / Security Analyst / User)
2. **Dashboard** — security score, active threats, statistics, trends, recent activity
3. **Real-Time Monitoring** — system logs, login attempts, network events
4. **AI Threat Detection (Model A)** — 9-class RandomForest classifier on synthetic event
   features (malware, DDoS, SQL injection, XSS, brute force, port scan, phishing, ransomware,
   insider threat)
5. **Temporal Threat Detection (Model B)** — attention-LSTM sequence model trained on the real
   **NSL-KDD** intrusion-detection dataset, with SHAP waterfall explainability per prediction
6. **Threat Classification** — Low / Medium / High / Critical severity
7. **Threat Intelligence** — CVE database, IOC feed, MITRE ATT&CK mapping
8. **Automated Incident Response** — tabular Q-learning agent selects a proportionate response
   action (block IP, disable user, quarantine, notify, escalate) for each detected threat
9. **AI Security Assistant** — Groq-backed (Llama 3.3 70B) conversational chat with real
   session memory, answering both platform-specific and general questions; falls back to an
   offline keyword-matcher if the Groq API is unreachable
10. **Incident Management** — create, assign, update, resolve, timeline, comments
11. **Reports** — PDF, CSV, Excel export
12. **Notifications** — dashboard + email alerts for critical threats
13. **Visual Analytics** — line/pie/bar charts, threat & incident trends
14. **Audit Logs** — full action trail
15. **Settings** — profile, password, notification preferences, API configuration

</details>

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 🏗️ Architecture

```mermaid
graph TD
    A["⚛️ React Frontend<br/>(Vercel)"] -->|REST + JWT| B["☕ Spring Boot API<br/>(Render)"]
    B -->|JDBC / JPA| C[("🗄️ Aiven MySQL")]
    B -->|REST via WebClient| D["🤖 FastAPI AI Service<br/>(Render)"]
    D -->|9-class classification<br/>+ AI Assistant replies| B

    style A fill:#61DAFB,color:#000
    style B fill:#6DB33F,color:#fff
    style C fill:#4479A1,color:#fff
    style D fill:#009688,color:#fff
```

The React SPA talks only to the Spring Boot API (never directly to the AI service or database).
The backend owns all persistence via JPA/MySQL and delegates threat classification + assistant
replies to the FastAPI microservice over REST, with a graceful heuristic fallback if that service
is temporarily unreachable.

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 🧰 Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | React 18 (Vite), React Router, Axios, Tailwind CSS, Recharts |
| **Backend** | Spring Boot 3.2.5, Spring Web, Spring Data JPA, Maven |
| **AI Service** | Python 3.11, FastAPI, scikit-learn, PyTorch (attention-LSTM), SHAP, Pandas, NumPy |
| **AI Assistant LLM** | Groq API (Llama 3.3 70B), offline keyword-matcher fallback |
| **Datasets** | Synthetic event features (Model A), real **NSL-KDD** intrusion dataset (Model B) |
| **Database** | MySQL 8 (Aiven managed, in production) |
| **Authentication** | Spring Security + JWT (access + refresh tokens), BCrypt password hashing |
| **Deployment** | Docker (all three services), Vercel (frontend), Render (backend + AI service) |
| **Containerization** | Multi-stage Dockerfiles per service + Docker Compose for local parity |

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 📸 Screenshots

> Captured directly from the live deployed app at
> [ai-based-cyber-threat-detection-and.vercel.app](https://ai-based-cyber-threat-detection-and.vercel.app).

#### Dashboard
![Dashboard](docs/screenshots/dashboard.png)

#### Threat Detection
![Threat Detection](docs/screenshots/threat-detection.png)

#### Analytics
![Analytics](docs/screenshots/analytics.png)

#### Reports
![Reports](docs/screenshots/reports.png)

#### Notifications
![Notifications](docs/screenshots/notifications.png)

#### AI Assistant
![AI Assistant](docs/screenshots/ai-assistant.png)

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 🛠️ Installation

### Prerequisites

- Java 17+ and Maven 3.9+
- Node.js 18+ and npm
- Python 3.11+
- MySQL 8.0+
- (Optional) Docker & Docker Compose

### 1️⃣ Database

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql
```

Creates the `cyberguard_db` schema with all tables, seeded roles/users
(`admin` / `analyst` / `user`, all with password `Password@123`), sample CVEs, IOCs, and
MITRE ATT&CK techniques.

### 2️⃣ AI Service (FastAPI)

```bash
cd ai-service
python -m venv venv
source venv/bin/activate        # venv\Scripts\activate on Windows
pip install -r requirements.txt
cp .env.example .env             # add your GROQ_API_KEY (free at console.groq.com)
python -m app.ml.train_model     # Model A: RandomForest on synthetic features (~10s)
python -m app.ml.train_lstm      # Model B: attention-LSTM on NSL-KDD (requires dataset/KDDTrain.parquet + KDDTest.parquet)
python -m app.ml.response_policy # trains the Q-learning response-selection agent
uvicorn app.main:app --reload --port 8000
```

Verify: http://localhost:8000/health · interactive docs at http://localhost:8000/docs

> The AI Security Assistant works without `GROQ_API_KEY` set — it just falls back to the
> offline keyword-matcher instead of calling the LLM. Get a free key (no credit card required)
> at [console.groq.com](https://console.groq.com).

### 3️⃣ Backend (Spring Boot)

```bash
cd backend
cp .env.example .env    # edit DB credentials / JWT secret as needed
mvn spring-boot:run
```

Verify: http://localhost:8080/swagger-ui.html

### 4️⃣ Frontend (React + Vite)

```bash
cd frontend
cp .env.example .env    # set VITE_API_BASE_URL if backend isn't on localhost:8080
npm install
npm run dev
```

Visit http://localhost:5173 and log in with the seeded admin account.

### 🐳 Or run everything with Docker Compose

```bash
docker compose up --build
```

Spins up MySQL (schema + seed auto-loaded), the AI service, the backend, and an
Nginx-served frontend build — all wired together with the same environment variable
contracts used in production.

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 🔑 Environment Variables

> Real credentials are **never** committed. Copy the relevant `.env.example` file in each
> service directory and fill in your own values.

**`backend/.env.example`**
```env
SERVER_PORT=8080
DB_URL=jdbc:mysql://localhost:3306/cyberguard_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=root
DDL_AUTO=update

JWT_SECRET=change-this-to-a-strong-base64-secret-in-production
JWT_ACCESS_EXP=900000
JWT_REFRESH_EXP=604800000

CORS_ALLOWED_ORIGINS=http://localhost:5173

AI_SERVICE_URL=http://localhost:8000

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
```

**`frontend/.env.example`**
```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

**`ai-service/.env.example`**
```env
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:8080
CONFIDENCE_THRESHOLD=0.35
GROQ_API_KEY=
```

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 📚 API Documentation

Full interactive docs are auto-generated and served live:

- **Swagger UI** (backend): `/swagger-ui.html` · **OpenAPI JSON**: `/api-docs`
- **AI Service docs**: `/docs`

All backend routes are prefixed `/api/v1` and (except `/auth/**`) require an
`Authorization: Bearer <accessToken>` header.

<details>
<summary><strong>🔐 Authentication</strong></summary>

| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Create a new account (default role `ROLE_USER`) |
| POST | `/auth/login` | Authenticate and receive access + refresh tokens |
| POST | `/auth/refresh` | Exchange a refresh token for a new token pair |
| POST | `/auth/logout` | Revoke a refresh token |

</details>

<details>
<summary><strong>🛡️ Threats</strong></summary>

| Method | Path | Description |
|---|---|---|
| POST | `/threats/detect` | Submit raw event features; classified by the AI service, persisted, auto-responded to |
| GET | `/threats` | Paginated list, filterable by `severity` / `type` |
| GET | `/threats/recent` | Last 10 detected threats |
| GET | `/threats/{id}` | Threat detail |
| PATCH | `/threats/{id}/status` | Update threat status (Admin/Analyst) |

</details>

<details>
<summary><strong>🚨 Incidents</strong></summary>

| Method | Path | Description |
|---|---|---|
| POST | `/incidents` | Create an incident |
| GET | `/incidents` | Paginated list, filterable by `status` |
| GET | `/incidents/recent` | Last 10 incidents |
| GET | `/incidents/{id}` | Incident detail |
| PATCH | `/incidents/{id}` | Update status / assignee / resolution notes |
| POST | `/incidents/{id}/comments` | Add a comment |
| GET | `/incidents/{id}/timeline` | Full audit timeline for the incident |

</details>

<details>
<summary><strong>📄 Reports</strong></summary>

| Method | Path | Description |
|---|---|---|
| GET | `/reports/threats/csv` | Threats report (CSV) |
| GET | `/reports/threats/excel` | Threats report (XLSX) |
| GET | `/reports/incidents/csv` | Incidents report (CSV) |
| GET | `/reports/incidents/pdf` | Incidents report (PDF) |

</details>

<details>
<summary><strong>📈 Analytics</strong></summary>

| Method | Path | Description |
|---|---|---|
| GET | `/analytics/overview` | Consolidated analytics: trends, MITRE distribution, resolution time, top sources/assets (Admin/Analyst) |

</details>

<details>
<summary><strong>💬 AI Assistant</strong></summary>

| Method | Path | Description |
|---|---|---|
| POST | `/assistant/chat` | Send a message; returns a reply and session id |
| GET | `/assistant/sessions` | List the current user's chat sessions |
| GET | `/assistant/sessions/{id}/messages` | Full message history for a session |

</details>

<details>
<summary><strong>🤖 AI Service (direct)</strong></summary>

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/predict` | Model A: classify event features into one of 9 threat types + severity + confidence + recommended action |
| POST | `/api/v1/predict/temporal` | Model B: attention-LSTM sequence classification (NSL-KDD schema) with SHAP explainability |
| POST | `/api/v1/assistant/chat` | Groq-backed (Llama 3.3) cybersecurity assistant, with offline keyword-matcher fallback |
| GET | `/health` | Liveness check |

</details>

See [docs/API_DOCUMENTATION.md](docs/API_DOCUMENTATION.md) for the complete endpoint reference,
including Threat Intelligence, Monitoring, Notifications, Audit Logs, Users, and Settings.

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## ☁️ Deployment

| Service | Platform |
|---|---|
| Frontend | **Vercel** |
| Backend (Spring Boot) | **Render** |
| AI Service (FastAPI) | **Render** |
| Database | **Aiven MySQL** (managed) |

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the full deployment guide.

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 🌟 Project Highlights

- 🏛️ Full-stack, three-service architecture (React · Spring Boot · FastAPI)
- 🤖 Real AI/ML integration — two independently trained models, not a mocked response:
  a scikit-learn RandomForest (Model A) and a PyTorch attention-LSTM trained on the real
  **NSL-KDD** dataset (Model B), with SHAP explainability on every Model B prediction
- 🧮 A genuinely-trained tabular Q-learning agent selects proportionate incident-response actions
- 💬 Real LLM-backed AI Assistant (Groq / Llama 3.3 70B) with conversation memory, not keyword matching
- 🔐 JWT-based authentication with refresh-token rotation and RBAC
- 🐳 Dockerized services with dedicated multi-stage Dockerfiles
- ☁️ Actually deployed to production (Vercel + Render + Aiven), not just "deployable"
- 🔌 Clean REST API design, documented via OpenAPI/Swagger
- 🔔 Real-time notifications via Server-Sent Events
- 🧯 Graceful degradation — AI classification falls back to a heuristic, and the AI Assistant
  falls back to an offline keyword-matcher, if the underlying service is ever unreachable

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 🔮 Future Enhancements

- [ ] Real-world network telemetry ingestion (e.g. NetFlow/Zeek) instead of manually submitted event features
- [ ] WebSocket-based collaborative incident war-rooms
- [ ] Multi-tenant organization support
- [ ] Replace NSL-KDD's synthetic row-windowing with true timestamped/session-grouped flow data for genuine temporal fidelity
- [ ] SOAR-style playbook automation for incident response
- [ ] Dark/light theme toggle and full WCAG AA accessibility audit
- [ ] Kubernetes deployment manifests for horizontal scaling

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

## 📄 License

This project is released under the **MIT License** — free to use, modify, and distribute
for educational and commercial purposes.

<hr>

<div align="right">

[⬆ Back to top](#-cyberguard)

</div>

---

<div align="center">

Built as a final-year engineering capstone project.

[⬆ Back to top](#-cyberguard)

</div>

<hr>

<div align="center">

[⬆ Back to top](#-cyberguard)

</div>
