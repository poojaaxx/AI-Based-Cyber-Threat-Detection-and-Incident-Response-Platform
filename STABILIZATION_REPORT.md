# CyberGuard stabilization report

Date: 2026-09-21. Existing branch: `realtime-threat-monitoring`.
The original working tree was retained. No reset, revert, discard, telemetry, collectors, packet capture, streaming implementation, fake operational events, or redesign was performed.

## Phase A: review of inherited modifications

Before editing, ran `git status`, `git diff --stat`, and the full `git diff`; inspected all five modified files and their callers/schema/security configuration. No AGENTS.md files were found in the repository.

| Inherited file | Change and audit issue | Correctness / regression assessment before stabilization | Initial completeness |
|---|---|---|---|
| frontend/src/pages/Login.jsx | Removed password values from demo account objects and stopped password autofill (#1) | Correct UI behavior, but a source comment still disclosed the password. No functional regression in username selection. | Partial |
| backend/src/main/java/com/cyberguard/platform/entity/User.java | Added mapped lockedUntil field (#2) | Mapping correct; schema/migration and database verification missing. Comment overstated null as meaning no lock, ignoring administrative LOCKED status. | Partial |
| backend/src/main/java/com/cyberguard/platform/service/AuthService.java | Added configurable attempt counter, temporary lock and expiry checks (#2) | Authentication exceptions rolled back persistence; lock check was outside its catch block; expiry unconditionally reactivated disabled users; no concurrent-attempt protection or positive configuration validation. | Partial / unsafe |
| backend/src/main/java/com/cyberguard/platform/service/IncidentService.java | Deleted hardcoded admin lookup helper (#3) | Caller still referenced deleted systemUser(), causing compile failure. Entity and SQL still required a reporter. | Partial / compile regression |
| backend/src/main/resources/application.yml | Added threshold/duration environment configuration (#2) | Correct wiring/defaults; application validation and accurate expiry documentation still needed. | Partial |

## Final changes by file

Java paths below are relative to `backend/src/main/java/com/cyberguard/platform/` unless otherwise stated.

| File | Result |
|---|---|
| frontend/src/pages/Login.jsx | Username-only selector; removed plaintext credential from source and build. |
| entity/User.java | Persisted locked_until mapping and corrected temporary-lock documentation. |
| service/AuthService.java | Positive configurable threshold/duration; failure-state commits on authentication rejection; serialized attempts; bounded lock; safe expiry; successful-login reset; locked/disabled refresh rejection. |
| repository/UserRepository.java | Pessimistic account lookup for login to prevent lost concurrent increments. |
| exception/GlobalExceptionHandler.java | Authentication failures return a safe 401 instead of a generic 500. |
| security/JwtAuthenticationFilter.java | Existing JWTs cannot authenticate disabled or locked accounts. |
| backend/src/main/resources/application.yml | AUTH_MAX_FAILED_ATTEMPTS (default 5), AUTH_LOCKOUT_DURATION_MINUTES (default 15), expiry documentation. |
| service/IncidentService.java | Transactional automated creation with null system reporter; removed hardcoded admin dependency. |
| entity/Incident.java | Nullable reported_by relationship, matching system-generated incidents. |
| controller/ThreatController.java | Explicit ADMIN/ANALYST/USER read/explain/investigate authorization; ADMIN/ANALYST mutation and temporal prediction authorization. Existing frontend standard-user read access preserved. |
| controller/NotificationController.java | Passes authenticated user ID for mark-read. |
| repository/NotificationRepository.java | Ownership-scoped ID lookup. |
| service/NotificationService.java | Owner-only mark-read; administrator notification count used to determine response outcome. |
| service/ResponseActionService.java | Loads and flushes quarantine status; explicit outcome statuses; block requests PENDING; truthful database-only details; no-recipient notification FAILED; execution errors no longer invoke duplicate fallback playbook. |
| backend/pom.xml | Replaced broad iText aggregate POM dependency with layout 7.2.5 (transitive kernel/io); H2 dependency limited to tests. |
| database/schema.sql | Fresh schema includes locked_until and nullable reported_by. |
| database/migrations/20260921_stabilization.sql | Rerunnable existing-MySQL migration for both fields. |
| backend/src/test/java/com/cyberguard/platform/service/ResponseActionServiceTest.java | Retained existing tests; added failure/no-fallback/no-false-success regression. |
| backend/src/test/java/com/cyberguard/platform/service/StabilizationPersistenceTest.java | Real JPA/database tests in separate committed transactions, including concurrency. |
| backend/src/test/java/com/cyberguard/platform/service/StabilizationWebTest.java | HTTP security/ownership tests using the real security configuration and method authorization. |
| backend/src/test/java/com/cyberguard/platform/service/ReportServiceTest.java | Runtime generation and PDF-reader validation of threat, incident and risk-summary PDFs. |
| STABILIZATION_REPORT.md | Review, results, file list, deployment limits and feature matrix. |

## Commands and results

| Command / check | Result |
|---|---|
| git status; git diff --stat; git diff | Reviewed original five modifications before editing. |
| npm run build (frontend) | PASS; 2,423 modules transformed. Initial sandbox directory-access failure resolved with approved elevated rerun. |
| mvn compile (backend, JAVA_HOME=C:/Program Files/Java/jdk-17) | PASS. Initial sandbox Maven-cache access failure resolved with approved elevated rerun. Existing JwtUtil deprecation warning remains. |
| mvn test | PASS: initial 14 tests, zero failures/errors/skips. |
| mvn test -l target/stabilization-test.log | PASS: final 20 tests, zero failures/errors/skips; recompiles final executable changes. |
| AI venv Python: import app.main.app, instantiate FastAPI TestClient, GET /, assert HTTP 200 | PASS: service reports running; imports/startup unaffected. AI source unchanged. |
| rg credential scan of frontend/src and frontend/dist | Removed demo password absent. |
| git diff --check | PASS. |

Maven test evidence: `backend/target/surefire-reports/` and `backend/target/stabilization-test.log` (generated, not committed).

## Workflow verification and updated feature matrix

| Feature / workflow | Status | Evidence / limits |
|---|---|---|
| Frontend credential removal | Complete | Source scan and successful production build; selector never supplies a password. |
| Successful login | Verified | Actual BCrypt authentication provider, JPA writes and refresh token persistence; JWT generation mocked in persistence tests. |
| Failed login | Verified | Counter persists after exception across transactions. |
| Lockout threshold and duration | Verified | Test config uses 3 attempts / 2 minutes; timestamp checked against configured interval; nonpositive config rejected. |
| Correct password while locked | Verified | LockedException; timestamp unchanged; rejected attempt persisted. |
| Login after lock expiry | Verified | Timestamp advanced into past in DB; real authentication succeeds; status ACTIVE. No wall-clock delay needed. |
| Successful-login reset | Verified | Failure count zero; next failure starts at one; lockedUntil cleared. |
| Expired lock with wrong password | Verified | New failure window starts at one and commits. |
| Disabled/manual-lock safety | Verified | Expiry does not reactivate disabled account; administrative lock without expiry remains locked. |
| Concurrent login failures | Verified | Three simultaneous calls persist all three failures and lock the account. |
| lockedUntil persistence | Verified in H2 | Reloaded from database outside the login transaction; stored lock survives authentication exception. Existing MySQL migration not executed here. |
| Automated incident without admin | Verified | Incident and timeline persisted; no admin exists; reporter null. |
| Threat authorization | Verified | Anonymous 401; unknown role 403; ADMIN/ANALYST/USER can read/explain/investigate; standard-user mutations denied; operators allowed. |
| Notification ownership | Verified | Cross-user HTTP request 404; no save; owner succeeds; real repository query and database read flag tested separately. |
| Quarantine persistence | Verified | Detached request threat reloaded; MITIGATED re-read after transaction. This is application status only. |
| Response SUCCESS | Verified | Quarantine and account disable persist their actual local changes. Incident/notification success describes local records. |
| Response PENDING | Verified | IP block request saved but no network enforcement claimed. |
| Response FAILED / exceptions | Verified | Zero admin recipients produces FAILED; missing threats and persistence failures cannot record SUCCESS; execution failure does not retry fallback. Transaction errors roll back rather than retaining a separate FAILED row. |
| Disabled/locked existing JWT | Verified | HTTP 401 for either status. Refresh issuance is also guarded. |
| PDF generation | Verified at runtime | Three report types generated and parsed with iText; page count and extracted branding checked. No browser download or visual layout review performed. |
| AI service imports/startup | Verified | In-process HTTP smoke check; no model-inference benchmark or external AI dependency check. |
| Live network enforcement | Not implemented | Block is PENDING; quarantine is database state only. |
| Telemetry/packet capture/collectors/new streaming | Outside scope | Not implemented or started. |

## Incomplete deployment work and remaining risks

All eight requested code fixes are implemented. Existing MySQL deployments must run `database/migrations/20260921_stabilization.sql` against the application's selected database before startup. It adds locked_until when absent and makes incidents.reported_by nullable. The fresh schema and entity mappings match. The migration was provided but not applied to any existing MySQL database; H2 tests do not establish MySQL migration/locking behavior in that deployment.

Tests exercise service transactions, real database persistence and HTTP authorization in isolation; this was not a full browser-to-running-MySQL acceptance run. Standard users retain global threat-read permissions because the existing frontend explicitly exposes those pages; narrowing that policy would require a separately agreed access-model change.

No firewall or host isolation adapter exists. IP blocks remain pending indefinitely until enforcement is implemented in a future scope. Database exceptions roll back the response transaction and do not retain a separate failure record. Dashboard notification SUCCESS does not certify email delivery.

Historical response rows and already configured demo accounts were not rewritten or rotated. This removes frontend credential exposure; it does not invalidate previously distributed credentials. Existing JwtUtil compiler deprecation warning remains.

Stabilization ends here. Real-time monitoring work requires a new explicit request.
