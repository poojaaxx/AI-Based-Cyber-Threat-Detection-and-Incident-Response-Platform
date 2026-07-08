# Installation Guide

## Prerequisites

- Java 17+ and Maven 3.9+
- Node.js 18+ and npm
- Python 3.11+
- MySQL 8.0+
- (Optional) Docker & Docker Compose for containerized setup

## Option A — Docker Compose (fastest)

From the repository root:

```bash
docker compose up --build
```

This starts MySQL (with schema + seed data auto-loaded), the AI service, the backend,
and the frontend. Visit http://localhost:5173 and sign in with `admin` / `Password@123`.

## Option B — Manual Setup

### 1. Database

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql
```

This creates the `cyberguard_db` schema with all tables, roles, seeded users
(admin / analyst / user, all with password `Password@123`), sample CVEs, IOCs,
and MITRE ATT&CK techniques.

### 2. AI Service (FastAPI)

```bash
cd ai-service
python -m venv venv
source venv/bin/activate        # venv\Scripts\activate on Windows
pip install -r requirements.txt
python -m app.ml.train_model    # trains and saves the RandomForest model (~10s)
uvicorn app.main:app --reload --port 8000
```

Verify: http://localhost:8000/health and interactive docs at http://localhost:8000/docs

### 3. Backend (Spring Boot)

```bash
cd backend
cp .env.example .env   # edit DB credentials / JWT secret as needed
mvn spring-boot:run
```

Or build a jar and run it directly:

```bash
mvn clean package -DskipTests
java -jar target/platform-1.0.0.jar
```

Verify: http://localhost:8080/swagger-ui.html

Environment variables (see `backend/.env.example` / `backend/src/main/resources/application.yml`):

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | JDBC connection string | `jdbc:mysql://localhost:3306/cyberguard_db` |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL credentials | `root` / `root` |
| `JWT_SECRET` | Base64 HMAC secret for signing JWTs | (dev default provided) |
| `AI_SERVICE_URL` | Base URL of the FastAPI AI service | `http://localhost:8000` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API | `http://localhost:5173` |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP config for critical alert emails | unset (email sending fails silently if unset) |

### 4. Frontend (React + Vite)

```bash
cd frontend
cp .env.example .env   # set VITE_API_BASE_URL if backend isn't on localhost:8080
npm install
npm run dev
```

Visit http://localhost:5173 and log in with the seeded admin account.

## Verifying the Full Stack

1. Log in as `admin` / `Password@123`.
2. Go to **Threats** → the list is empty until a detection is submitted. You can trigger
   one via Swagger UI: `POST /api/v1/threats/detect` with a sample payload, or wait for
   your own log/network ingestion pipeline to call that endpoint.
3. Go to **AI Assistant** and ask "What is ransomware?" to confirm the AI service chat
   endpoint is reachable.
4. Go to **Threat Intelligence** to see seeded CVE/IOC/MITRE ATT&CK data.
