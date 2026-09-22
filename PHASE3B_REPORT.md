# Phase 3B: genuine sampled Windows TCP monitoring

Implemented only the approved PowerShell TCP observer scope. Actual Windows socket metadata is persisted as linked `NetworkEvent` and `SecurityEvent` records, then published after commit through the existing authenticated SSE stream. Ordinary observations are `EVENT`, with no confidence, Threat, Incident, response action, or AI request. The optional controlled rule is disabled by default and also creates no Threat/Incident.

## Local setup and current state

The new migration has been applied to the local MySQL database and successfully reapplied to check idempotence. The local backend was restarted with collector ingestion enabled and a newly generated dedicated credential in the gitignored `backend/.env`. No credential is included in this report or committed files. The observer and demo processes were stopped after verification; the observer should therefore show `UNAVAILABLE` until started again. The backend remains running.

Environment settings in `backend/.env`:

```dotenv
COLLECTOR_ENABLED=true
COLLECTOR_ID=windows-local
COLLECTOR_INGEST_KEY=<dedicated secret of at least 32 characters>
COLLECTOR_DEMO_RULE_ENABLED=false
COLLECTOR_DEMO_PORT=19090
```

The committed `.env.example` defaults to disabled and contains no collector credential. Backend changes to these settings require a backend restart. Run only one observer for `windows-local`; a new session supersedes the previous session. Do not reuse a login token as the collector key.

For another database, apply [the Phase 3B migration](database/migrations/20260922_network_monitoring.sql) before starting the updated backend. In the MySQL client connected to the target database, from the repository directory:

```sql
USE cyberguard_db;
SOURCE database/migrations/20260922_network_monitoring.sql;
```

The migration adds local/remote metadata, connection/session/sequence identity, a unique delivery key, timestamps, the nullable network-event FK in `security_events`, and `collector_state`. Authentication linkage and legacy source/destination fields become nullable where required. Old records keep their original source/destination meaning. Bytes/packets stay NULL when unavailable. The already-applied stabilization migration was not changed. `database/schema.sql` also includes the fresh-install definitions.

## Exact collector and demo commands

Run these from the repository root in separate PowerShell terminals. Use a terminal with permission to query `Get-NetTCPConnection`; an elevated terminal was used for the verified Windows run.

Terminal 1 — load the existing local credential without printing it, then run the observer:

```powershell
$env:COLLECTOR_INGEST_KEY = ((Get-Content .\backend\.env | Where-Object { $_ -match '^COLLECTOR_INGEST_KEY=' } | Select-Object -Last 1) -split '=', 2)[1]
.\collectors\windows\Watch-NetworkConnections.ps1 -BackendUrl http://127.0.0.1:8080 -CollectorId windows-local
```

Terminal 2 — run the real TCP listener, bound only to loopback:

```powershell
.\collectors\windows\demo\Start-LocalTcpDemo.ps1 -Port 19090
```

Terminal 3 — create a real connection and hold it open for 15 seconds:

```powershell
.\collectors\windows\demo\Connect-LocalTcpDemo.ps1 -Port 19090 -HoldSeconds 15
```

The client prints its actual endpoints and PID. To independently inspect the connection while it is open:

```powershell
Get-NetTCPConnection -RemotePort 19090 -State Established |
    Select-Object LocalAddress, LocalPort, RemoteAddress, RemotePort, State, OwningProcess
```

## Evaluator demonstration

1. Sign in as ADMIN or ANALYST and open `http://localhost:5173/monitoring`. Select **Network** within **LIVE SECURITY EVENTS**. Keep this page open.
2. Start the observer in Terminal 1. Verify separate labels: **Dashboard stream: CONNECTED** and **Windows TCP observer: AVAILABLE**. STARTING may be brief; the first sample is a baseline of `CONNECTION_PRESENT` rows.
3. Start the loopback listener in Terminal 2, then the held-open client in Terminal 3. Watch `CONNECTION_OBSERVED` appear without refreshing. A local loopback connection can produce two rows: the client socket and the server socket, with reversed local/remote endpoints. These are two real entries in the Windows TCP table.
4. Match the client endpoint/PID to its printed output and the independent PowerShell query. Detector is **EVENT**; the result explicitly makes no threat determination. There is no ML confidence.
5. Leave the client open. An unchanged socket must not produce a new event on every sample. State changes can generate `CONNECTION_STATE_CHANGED`.
6. Let the client close and run it again. The new connection produces new activity and a distinct connection ID. Removal is reported only after two successful samples no longer contain that tracked socket; TIME_WAIT can delay this.
7. Stop Terminal 1 with Ctrl+C. After approximately 15–20 seconds, the observer becomes **UNAVAILABLE**, while the dashboard stream remains **CONNECTED**. No artificial security alert is generated by the health timer.
8. Restart the observer with the same command. Verify a new session, new `CONNECTION_PRESENT` baseline, and recovery to AVAILABLE. Downtime is not represented as observed traffic.
9. Expand **Collector health and coverage** to see heartbeat/sample timestamps, queue depth, dropped count, and gaps. Expand a row's **Timing / evidence** to inspect UTC observation/ingestion timestamps, connection ID, session, and sequence.
10. Switch to **Authentication** or **All** to see the existing authentication feed. Ordinary network observations do not alter threat or incident totals.

Optional controlled-rule demonstration: set `COLLECTOR_DEMO_RULE_ENABLED=true`, restart the backend, restart the observer, wait for AVAILABLE, and then open a new demo connection. Only a newly tracked established client-side loopback connection to port 19090 matches `LOCAL_DEMO_ENDPOINT_V1`, once per connection. Baseline connections are excluded. The display is **RULE — Controlled demo endpoint matched; not classified as malicious**. It has no confidence, threat, incident, or blocking action. Set the setting back to false and restart the backend when finished. This optional path was verified by automated backend tests; the real-socket run below used the default disabled setting.

## Implementation and health behavior

- Sampling targets a one-second cycle, with no overlapping queries. A failed sample cannot imply disappearance. Sampling failure or a gap over five seconds resets the next successful sample to a new baseline.
- Identity uses endpoints, owning PID, and usable creation time, plus a generated tracking ID. TCP state is not part of identity. The collector excludes its own backend socket and the corresponding loopback server endpoint when identifiable in the sampled table; retained endpoint exclusions cover subsequent PID-less TIME_WAIT entries.
- The collector only emits metadata: endpoints, TCP state, PID, safe process name, available connection creation time, observation time, and identity fields. It never reads packet contents, process command lines, executable paths, HTTP data, or browser/session credentials.
- A dedicated key authorizes only `/api/v1/collector/start`, `/heartbeat`, and `/observations`. Requests must originate directly from loopback. Forwarded client-address headers are rejected. User JWTs cannot substitute for this key. Unknown JSON fields, including claimed provenance, are rejected.
- Ingestion is bounded to 100 observations and 256 KiB per request. The server validates fields and timestamps and assigns EVENT/RULE provenance. The unique `(collector_id, session_id, sequence_number)` key makes retries idempotent. A transaction failure rolls back the whole batch, including its SSE publication.
- The in-memory queue holds at most 1,000 observations by default. Overflow drops new observations and increments a visible counter. Retries back off from two to 30 seconds, retaining the original IDs/timestamps. Pending observations are lost when the process exits; its final output reports unsent count. No disk spool is created.
- Heartbeats target five seconds. The server checks health every five seconds and marks heartbeats older than 15 seconds UNAVAILABLE. Thus a visible transition generally takes 15–20 seconds. A live heartbeat with stale/failed sampling, a delivery error, or queue depth over 100 is DEGRADED. DISABLED and STARTING are distinct states.
- Collector health is independent of SSE health. While the browser stream is disconnected, the UI identifies collector status as last reported/unverified. Reconnect retrieves current status and persisted event history. Both `security-event` and `collector-status` use the existing ADMIN/ANALYST-only monitoring audience and after-commit SSE publication.
- The panel keeps the latest 100 matching rows and has All/Authentication/Network filters. Network columns are explicit local/remote endpoints, not inferred traffic direction. The stored Network Events table also consumes SSE payloads directly, avoiding an HTTP request for each socket event. No threat-summary recalculation is triggered by ordinary network events.

## Verification evidence

Real local end-to-end test on **2026-09-22**:

| Check | Result |
|---|---|
| Actual Windows collector registration and sampling | STARTING → AVAILABLE; DEGRADED was also reported during startup/backlog |
| First held-open connection | Event 161 / NetworkEvent 142, `127.0.0.1:61839 ↔ 127.0.0.1:19090`, EVENT |
| First observation / ingestion | `14:19:28.832600900Z` / `14:19:28.995533400Z` |
| Reopened connection | Event 179 / NetworkEvent 160, local port 61843, distinct connection ID, EVENT |
| Second observation / ingestion | `14:19:44.959851600Z` / `14:19:45.093677100Z` |
| Endpoint accuracy | Both matched independent Windows TCP queries, including owning client PID |
| Unchanged socket | One established observation per tracked client connection |
| Exact retry of real event | Inserted 0 new rows |
| Ordinary network effects | Threats 60 → 60; incidents 39 → 39 |
| Stop/restart | UNAVAILABLE received on the same live SSE connection; restart recovered with a new session/baseline |

Session IDs were `530741db-7760-47fb-823e-5f152fafc747` and `b030f1d0-d56f-4146-9ec3-28b03a8de1d6`. Local evidence is in `backend/target/phase3b-live-result.json` (ignored build output).

After the network test, the separate Phase 3A regression exercised real HTTP successful/failed logins, threshold 5, locked attempts, RULE evidence with null confidence, linked incident/response/notification, authorization, and reconnect. It passed, intentionally creating authentication threat **63** / incident **40** for QA account `phase3a.qa.1790086896`. Those records are authentication-test results, not network findings. Its source time was `2026-09-22T14:21:38.397547200Z`.

Automated checks:

- Backend: **32 tests passed, 0 failures/errors**, using `mvn test` with Java 17, including existing Phase 3A regressions plus network ingestion, access control, deduplication, rollback, commit-only publication, collector health, and optional rule isolation.
- PowerShell: `& .\collectors\windows\tests\Test-Observer.ps1` passed baseline, unchanged suppression, both ingestion endpoint exclusions, failed-sample recovery, stable identity through state changes, successful-sample removal, reconnect, bounded buffer/overflow, and delivery-health checks. Deterministic fixtures stay inside tests and are never sent to the running app.
- Frontend: `node --test tests/eventStream.test.mjs` passed split-frame parsing, collector-status dispatch, independent SSE/collector health, reconnection, stale-stream detection, and cleanup.
- Frontend production build: `npm run build` passed. Windows sandbox directory restrictions required running the build with approved expanded permissions.
- MySQL migration: applied and reapplied successfully. AI service/client implementation and model workflows were not modified; network service tests assert zero AI-client calls.

## Manual visual checks still required

The connected-browser inventory returned no browsers or apps. No screenshot or browser-rendered visual result is claimed. Actual authenticated SSE delivery was verified, and the frontend transport test passed, but manually confirm:

- Monitoring renders correctly and the wide endpoint table scrolls/readably displays columns.
- All/Authentication/Network filters and timing details work in the browser.
- Rows and collector labels update without a manual refresh.
- Stopping the collector keeps the visible dashboard stream CONNECTED while collector health becomes UNAVAILABLE.
- A browser/network interruption produces truthful RECONNECTING/DISCONNECTED states and recovers history/status on reconnection.
- If enabling the optional rule, verify its RULE label and explicit harmless wording visually.

## Changed/new files

Backend paths below are relative to `backend/src/main/java/com/cyberguard/platform/`:

- New: `controller/CollectorIngestionController.java`, `dto/request/CollectorRequests.java`, `entity/CollectorState.java`, `repository/CollectorStateRepository.java`, `security/CollectorAuthenticationFilter.java`, `service/NetworkCollectorService.java`.
- Updated: `config/SecurityConfig.java`, `controller/MonitoringController.java`, `entity/NetworkEvent.java`, `entity/SecurityEvent.java`, `repository/NetworkEventRepository.java`, `repository/SecurityEventRepository.java`, `service/SseHubService.java`.
- Configuration: `backend/src/main/resources/application.yml`, `backend/.env.example`; local gitignored `backend/.env` holds the enabled setting and generated credential.
- Tests: new `backend/src/test/java/com/cyberguard/platform/service/NetworkCollectorServiceTest.java` and `CollectorIngestionSecurityTest.java`; updated `SseCommitTest.java` and `StabilizationWebTest.java` in the same directory.
- Database: new `database/migrations/20260922_network_monitoring.sql`; updated `database/schema.sql`.
- Frontend: `frontend/src/components/monitoring/LiveSecurityEvents.jsx`, `frontend/src/context/EventStreamContext.jsx`, `frontend/src/services/eventStreamService.js`, `frontend/src/pages/Monitoring.jsx`, `frontend/tests/eventStream.test.mjs`.
- Scripts: `collectors/windows/Watch-NetworkConnections.ps1`, `collectors/windows/demo/Start-LocalTcpDemo.ps1`, `collectors/windows/demo/Connect-LocalTcpDemo.ps1`, `collectors/windows/tests/Test-Observer.ps1`.
- Report: `PHASE3B_REPORT.md`. Live verification harness/results/logs are local ignored files under `backend/target/`.

## Limitations

This is sampled TCP-table observation, not packet capture or complete connection auditing. Connections shorter than the effective polling interval can be missed. The first Windows query took about 2.5 seconds during preflight; query time and HTTP retries can make cycles slower than one second. UDP, DNS names, packet/byte counts, duration accuracy, initiator direction, content, and ML feature sets are unavailable. Process name/creation time can be unavailable. IPv4 and ordinary IPv6 literals are validated; scope-suffixed IPv6 literals are not currently accepted and would produce a visible ingestion failure requiring follow-up if encountered.

Socket/PID reuse and missing creation timestamps limit identity accuracy. A gap/restart intentionally establishes a new baseline, so the same still-open socket can appear as PRESENT in a new observation session. The collector is not an installed Windows service and does not auto-start with Windows. Only one local collector ID/session is active at a time. The bounded queue is volatile; events older than one day are rejected. Only recent matching events are recovered into the panel after long disconnections; the database retains the stored history. Continuous collection grows database history; this phase adds no retention deletion policy.

No Model A/B classification, payload inspection, Sysmon, Suricata, Zeek, or further phase was implemented.
