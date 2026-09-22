# Windows TCP Observer (Phase 3B)

`Watch-NetworkConnections.ps1` samples `Get-NetTCPConnection` on the machine it runs on and
sends connection-metadata observations (local/remote IP+port, TCP state, owning process) to the
CyberGuard backend's collector-ingestion API. It never reads payload/packet contents, never runs
in the cloud, and never chooses a threat verdict — the backend always assigns `EVENT`
provenance for these observations, and ordinary connections never create a Threat or Incident.

This script stays on your Windows machine. It is not deployed to Render/Vercel/any cloud
service — it just points its outbound requests at whichever backend you tell it to.

## Local mode (loopback backend)

Matches the verified Phase 3B local demo — no changes from before.

```powershell
$env:COLLECTOR_INGEST_KEY = "<32+ char local dev key, e.g. from backend/.env>"
.\collectors\windows\Watch-NetworkConnections.ps1
```

`-BackendUrl` defaults to `http://127.0.0.1:8080` and `-CollectorId` defaults to
`windows-local`. The backend only needs `COLLECTOR_ENABLED=true`; `COLLECTOR_REMOTE_ENABLED`
is irrelevant here — loopback requests are always accepted when the collector is enabled.

## Production mode (deployed backend)

Set `COLLECTOR_REMOTE_ENABLED=true` and a real `COLLECTOR_INGEST_KEY` on the deployed backend
(see [`docs/DEPLOYMENT.md`](../../docs/DEPLOYMENT.md#5-remote-windows-collector-phase-3b-optional)),
then on the Windows laptop:

```powershell
$env:CYBERGUARD_BACKEND_URL = "https://<your-backend-domain>"
$env:COLLECTOR_INGEST_KEY   = "<the same secret configured on the backend>"
$env:COLLECTOR_ID           = "<a name for this laptop, e.g. pooja-laptop>"

.\collectors\windows\Watch-NetworkConnections.ps1
```

The public dashboard's Monitoring page should then show `Windows TCP observer: AVAILABLE` and
start showing real connections from this machine within a few sample cycles.

## Security model

- Authentication is a single dedicated header, `X-Collector-Key`, compared to
  `COLLECTOR_INGEST_KEY` in constant time. It is **not** a user JWT, admin credential, or
  password — do not put any of those in this script or its environment variables.
- The backend URL must be `https://` for any non-loopback host; the script refuses to start
  otherwise. Loopback hosts must stay plain `http://`.
- Every request also carries `X-Collector-Timestamp`; the backend rejects requests whose
  timestamp has drifted more than `COLLECTOR_MAX_CLOCK_SKEW_SECONDS` (default 300s) from its
  own clock, and applies a per-key rate limit (`COLLECTOR_RATE_LIMIT_PER_MINUTE`, default 300).
- Local (loopback, no proxy headers) requests are authenticated exactly as before — this is
  unaffected by whether remote mode is enabled.
- The collector cannot choose `MODEL_A`/`MODEL_B`/`RULE`, a confidence score, or a threat
  status — the backend always stamps `EVENT` provenance on what this script sends. AI models
  are never invoked for these observations.
- The script never logs the key, the raw request, or raw HTTP exception details — failures are
  reported generically and retried with exponential backoff.
- Requests are capped at 262,144 bytes and 100 observations per batch; delivery is idempotent
  per collector/session/sequence, so retries never double-count an observation.

## Rotating the collector key

1. Generate a new high-entropy value (32+ characters) — do not type it into chat or a commit.
2. Update `COLLECTOR_INGEST_KEY` on the backend (Render dashboard, or your `backend/.env` for
   local use) and redeploy/restart the backend.
3. Update `$env:COLLECTOR_INGEST_KEY` wherever the collector runs, and restart it.
4. Old sessions using the previous key stop authenticating immediately; no data is lost — the
   next successful `start` call opens a fresh session/baseline.

## Local demo fixtures

`demo/Start-LocalTcpDemo.ps1` and `demo/Connect-LocalTcpDemo.ps1` open and hold a real loopback
TCP connection purely so you have something predictable for the observer to pick up during a
demo. They never talk to the backend themselves and are not part of the production data path:

```powershell
.\collectors\windows\demo\Start-LocalTcpDemo.ps1 -Port 19090
# in another terminal, while the observer above is running:
.\collectors\windows\demo\Connect-LocalTcpDemo.ps1 -Port 19090 -HoldSeconds 15
```

## Known limitations

- Not an installed Windows service; it does not auto-start with Windows and stops if you close
  the terminal or press Ctrl+C.
- Only one collector session per `-CollectorId` is meaningfully tracked by the dashboard at a
  time; running two instances with the same ID will produce confusing/overlapping state.
- Restarting the script always starts a new baseline — the first sample after restart reports
  currently-present connections as newly observed rather than diffing against the prior run.
