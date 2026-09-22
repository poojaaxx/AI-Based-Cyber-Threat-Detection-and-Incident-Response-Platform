# Phase 3A: live authentication monitoring

Implemented only CyberGuard authentication monitoring. No network collector or external collector was installed. Authentication rules do not call FastAPI, Model A, Model B, or the adaptive response policy. Existing model workflows and AI-service files are unchanged.

## Runtime flow

Actual login request → existing account lock/counter → LoginAttempt → normalized SecurityEvent → optional threshold RULE finding → shared ThreatService persistence → existing static ResponseActionService / IncidentService / NotificationService → transaction commit → authenticated SSE.

Successful login, isolated bad credentials, already-locked rejection, and other authentication rejections are recorded as events. Only a known account reaching the configured consecutive failed-password threshold creates a finding. The existing pessimistic account lock serializes concurrent requests; the transition to LOCKED prevents repeated findings in that lockout episode. A unique threat detection key additionally prevents duplicate persistence for the same triggering attempt. After expiry/reset, a new episode can create a new finding.

RULE findings have null confidence, no temporal classification, no cross-model agreement, and real attempt IDs/timestamps/counts in JSON evidence. Normal events never enter threat persistence. Evidence only includes attempts recorded with the new FAILED outcome; failures from before installation can contribute to an existing account counter without having complete new-format evidence. A successful login before the demo resets this counter.

The security event links its login attempt, threat and incident. Observed/ingested/detected times are UTC instants; processing latency measures server request entry through processing before commit (not browser delivery latency). The live panel retains the latest 100 events and reconciles persisted history on connection establishment/reconnection. The paginated history API is `/api/v1/monitoring/security-events`.

## SSE and authorization

- Reuses `/api/v1/events/stream` with the existing Bearer token.
- `security-event` reaches only ADMIN/ANALYST subscriptions; its history endpoint has the same role restriction.
- Notification, dashboard-update and security-event sends wait for successful transaction commit. Rollback sends nothing.
- Heartbeats every 15 seconds; client considers a stream stale after 45 seconds without a frame, then reconnects with bounded backoff.
- The frontend shows CONNECTED, RECONNECTING or DISCONNECTED. No unconditional dashboard Live label remains.
- Token validity/account status and monitoring-role membership are rechecked before delivery. Token refresh uses the existing Axios refresh queue.
- No durable SSE broker: the database history supplies recovery. The UI displays a bounded recent window, not unlimited replay.

## Database and local services

Applied `database/migrations/20260922_authentication_monitoring.sql` to the local development database. It is safe to rerun and preserves historical records. It adds `security_events`, login outcome, RULE provenance/evidence/linkage, a unique detection key, and nullable threat confidence. `database/schema.sql` also includes the fresh-install schema.

Restarted the local backend and started the frontend. Backend: `http://localhost:8080`; frontend: `http://localhost:5173`. The existing AI service was left running and unmodified.

Runtime logs are in `backend/target/phase3a-runtime.log` and `backend/target/phase3a-frontend.log`.

## Validation

- All 25 backend tests pass (`backend/target/phase3a-test.log`). Tests cover real authentication persistence, configured threshold, simultaneous failures, one incident per lockout episode, null confidence, evidence linkage, no AI calls even with adaptive response enabled, authorization, and commit/rollback SSE behavior.
- `node --test tests/eventStream.test.mjs` from `frontend` passes: split CRLF frames, security-event callbacks, reconnect, stale stream detection, cleanup, and disconnected state without credentials.
- `npm run build` from `frontend` passes.
- Real HTTP/MySQL/SSE integration passed with dedicated account `phase3a.qa.1790048981`: one success, five bad-password attempts, two already-locked attempts, eight events, one RULE threat #61, one incident #38, response timeline and persisted administrator notification. Its temporary lock follows the configured expiry; its records are retained as genuine verification activity.
- Threshold event: observed `2026-09-22T03:49:43.213197400Z`, ingested `2026-09-22T03:49:43.341955100Z`, detected `2026-09-22T03:49:43.366852400Z`, processing latency 258 ms.
- Integration also confirmed USER cannot read the monitoring history and a new SSE connection receives a fresh handshake. Detailed output: `backend/target/phase3a-integration-result.json`.
- No connected browser was available to the browser automation tool. Visual browser verification remains unperformed; transport behavior was verified through real SSE and frontend automated tests.

## Evaluator demonstration

1. Open `http://localhost:5173/monitoring` as ADMIN or ANALYST. Keep this observer account separate from the account being tested. Wait for CONNECTED in LIVE SECURITY EVENTS.
2. In a private browser, sign in successfully to a dedicated standard demo account whose password you know. This resets any prior consecutive failure count. Watch a new SUCCESS row appear on the observer page without refresh; it has no threat/incident link.
3. Sign out in the private browser. Submit one incorrect password for the same demo account. Watch FAILED / No rule matched; no threat is created.
4. Continue incorrect-password submissions until the configured threshold. The current default and verified runtime threshold are five total failures (`AUTH_MAX_FAILED_ATTEMPTS`, default 5), so after step 3 submit four more.
5. Watch exactly one threshold row with RULE and “Repeated authentication failures / suspected brute force.” Follow its RULE threat link: confidence is Not applicable; attempt evidence is displayed; no Model A/B controls are shown for this finding.
6. Follow the row's Incident link, or the threat's Investigate link, to inspect the linked incident and response-action timeline. As ADMIN, open the notification bell to see the persisted RULE notification.
7. Submit another password while the demo account remains locked. Watch a LOCKED event with “Account already locked; no new finding”; the original threat and incident remain the only finding for that episode.
8. Expand Timing / evidence on the threshold row. Show the UTC source, ingestion and detection times, processing milliseconds, event ID, login-attempt ID and evidence timestamps. Compare these with the current UTC time during the demonstration.
9. To demonstrate transport recovery, briefly take the observer browser offline using its normal developer tools. A detected failure/stale heartbeat produces RECONNECTING; restore connectivity and observe CONNECTED plus recovered recent history. No alerts are generated by this transport test.

The existing response implementation records an IP block request as PENDING (no firewall enforcement) and changes a threat's database status for quarantine (no host isolation). The account's temporary lockout is real. Local client IP uses the direct request address; forwarded headers are not trusted for this feed, so a reverse proxy would need an explicit trusted-proxy configuration later.

Phase 3A ends here. Network monitoring remains unimplemented.
