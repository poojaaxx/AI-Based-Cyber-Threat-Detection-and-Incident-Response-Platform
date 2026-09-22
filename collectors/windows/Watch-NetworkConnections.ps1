#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$BackendUrl = $(if ($env:CYBERGUARD_BACKEND_URL) { $env:CYBERGUARD_BACKEND_URL } else { 'http://127.0.0.1:8080' }),
    [string]$CollectorId = $(if ($env:COLLECTOR_ID) { $env:COLLECTOR_ID } else { 'windows-local' }),
    [ValidateRange(500,10000)][int]$SampleIntervalMs = 1000,
    [ValidateRange(2,10)][int]$MissingSamples = 2,
    [ValidateRange(1,1000)][int]$BufferCapacity = 1000,
    [ValidateRange(0,86400)][int]$RunSeconds = 0
)
$ErrorActionPreference = 'Stop'
$endpoint = [Uri]$BackendUrl
$isLoopbackHost = $endpoint.Host -in @('127.0.0.1','localhost','[::1]','::1')
if ($endpoint.AbsolutePath -ne '/' -or $endpoint.Query -or $endpoint.UserInfo) {
    throw 'Backend URL must be a bare origin, for example https://cyberguard-backend.onrender.com or http://127.0.0.1:8080.'
}
if ($isLoopbackHost) {
    if ($endpoint.Scheme -ne 'http') { throw 'Use plain HTTP for a loopback backend origin, for example http://127.0.0.1:8080.' }
} else {
    if ($endpoint.Scheme -ne 'https') { throw 'Use HTTPS for a remote backend origin, for example https://cyberguard-backend.onrender.com. The deployed backend must also have COLLECTOR_REMOTE_ENABLED=true.' }
}
if ($CollectorId -notmatch '^[a-zA-Z0-9_-]{1,60}$') { throw 'Invalid collector ID.' }
if ([string]::IsNullOrWhiteSpace($env:COLLECTOR_INGEST_KEY) -or $env:COLLECTOR_INGEST_KEY.Length -lt 32) {
    throw 'Set the dedicated COLLECTOR_INGEST_KEY environment variable (at least 32 characters).'
}
$sessionId = [guid]::NewGuid().ToString()
$script:sequence = 0L
$script:dropped = 0L
$script:queue = New-Object 'System.Collections.Generic.Queue[object]'
$connections = @{}
$controlSockets = @{}
$baseline = $true
$gapCount = 0L
$sampleError = $null
$deliveryError = $null
$lastSample = $null
$registered = $false
$retryAt = [DateTimeOffset]::MinValue
$retryDelay = 2
$heartbeatAt = [DateTimeOffset]::MinValue
$started = [DateTimeOffset]::UtcNow

function Send-CollectorRequest([string]$Path, $Body) {
    $json = ConvertTo-Json -InputObject $Body -Depth 6 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    if ($bytes.Length -gt 262144) { throw 'Collector batch exceeds the allowed size.' }
    try {
        $null = Invoke-RestMethod -Method Post -Uri ($BackendUrl.TrimEnd('/') + '/api/v1/collector/' + $Path) `
            -Headers @{ 'X-Collector-Key' = $env:COLLECTOR_INGEST_KEY; 'X-Collector-Timestamp' = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() } `
            -ContentType 'application/json' -Body $bytes -TimeoutSec 5
        return $true
    } catch {
        # Never print the request, credential, or raw HTTP exception.
        return $false
    }
}

function Add-Observation($Record, [string]$EventType, [string]$ObservedAt) {
    $script:sequence++
    $item = [ordered]@{
        localIp=$Record.LocalIp; localPort=$Record.LocalPort
        remoteIp=$Record.RemoteIp; remotePort=$Record.RemotePort
        protocol='TCP'; tcpState=$Record.State
        processId=$Record.ProcessId; processName=$Record.ProcessName
        connectionCreatedAt=$Record.CreatedAt; observedAt=$ObservedAt
        sequence=$script:sequence; connectionId=$Record.ConnectionId; eventType=$EventType
    }
    if ($script:queue.Count -ge $BufferCapacity) { $script:dropped++; return }
    $script:queue.Enqueue($item)
}

Write-Host "Windows TCP observer: $CollectorId; session $sessionId; backend $BackendUrl. Sampled metadata only. Ctrl+C stops."
try {
    while ($RunSeconds -eq 0 -or ([DateTimeOffset]::UtcNow - $started).TotalSeconds -lt $RunSeconds) {
        $cycle = [Diagnostics.Stopwatch]::StartNew()
        $now = [DateTimeOffset]::UtcNow
        if (!$registered -and $now -ge $retryAt) {
            $registered = Send-CollectorRequest 'start' @{ collectorId=$CollectorId; sessionId=$sessionId }
            if (!$registered) {
                $retryAt=$now.AddSeconds($retryDelay); $retryDelay=[Math]::Min(30,$retryDelay*2)
            } else { $retryDelay=2; $retryAt=$now; Write-Host 'Collector session registered.' }
        }
        try {
            $snapshot = @(Get-NetTCPConnection -ErrorAction Stop)
            if ($snapshot.Count -gt 20000) { throw 'TCP table exceeds observation capacity.' }
            $sampleTime = [DateTimeOffset]::UtcNow
            if ($lastSample -and ($sampleTime - $lastSample).TotalSeconds -gt 5 -and !$baseline) {
                $baseline=$true; $gapCount++
            }
            if ($baseline) { $connections=@{} }
            $seen=@{}
            $names=@{}
            # This process's own outbound connections to the backend (loopback or remote) must never
            # appear as observations. Matching on owning PID + remote port covers both cases without
            # depending on the backend's remote address, which varies for a cloud-hosted origin.
            foreach ($socket in $snapshot) {
                if ($socket.OwningProcess -eq $PID -and $socket.RemotePort -eq $endpoint.Port) {
                    $forward=@($socket.LocalAddress,$socket.LocalPort,$socket.RemoteAddress,$socket.RemotePort) -join '|'
                    $reverse=@($socket.RemoteAddress,$socket.RemotePort,$socket.LocalAddress,$socket.LocalPort) -join '|'
                    $controlSockets[$forward]=$sampleTime; $controlSockets[$reverse]=$sampleTime
                }
            }
            foreach ($controlKey in @($controlSockets.Keys)) {
                if (($sampleTime - $controlSockets[$controlKey]).TotalSeconds -gt 300) { $controlSockets.Remove($controlKey) }
            }
            if ($controlSockets.Count -gt 2000) {
                foreach ($controlKey in @($controlSockets.Keys | Sort-Object { $controlSockets[$_] } | Select-Object -First ($controlSockets.Count-2000))) {
                    $controlSockets.Remove($controlKey)
                }
            }
            foreach ($socket in $snapshot) {
                if ($socket.State.ToString() -in @('Listen','Bound','Closed')) { continue }
                $endpointKey=@($socket.LocalAddress,$socket.LocalPort,$socket.RemoteAddress,$socket.RemotePort) -join '|'
                if ($controlSockets.ContainsKey($endpointKey)) { continue }
                $creation=$null
                if ($socket.CreationTime -and $socket.CreationTime.Year -gt 1970) {
                    $creation=([DateTimeOffset]$socket.CreationTime).ToUniversalTime().ToString('o')
                }
                $key = @($socket.LocalAddress,$socket.LocalPort,$socket.RemoteAddress,$socket.RemotePort,$socket.OwningProcess,$creation) -join '|'
                $seen[$key]=$true
                if (!$connections.ContainsKey($key)) {
                    $owningPid=[long]$socket.OwningProcess
                    $name=$null
                    if ($owningPid -gt 0) {
                        if (!$names.ContainsKey($owningPid)) {
                            try { $names[$owningPid]=(Get-Process -Id $owningPid -ErrorAction Stop).ProcessName }
                            catch { $names[$owningPid]=$null }
                        }
                        $name=$names[$owningPid]
                        if ($name -and $name.Length -gt 120) { $name=$null }
                    }
                    $record=[pscustomobject]@{
                        LocalIp=$socket.LocalAddress; LocalPort=[int]$socket.LocalPort
                        RemoteIp=$socket.RemoteAddress; RemotePort=[int]$socket.RemotePort
                        ProcessId=$owningPid; ProcessName=$name; CreatedAt=$creation
                        State=$socket.State.ToString(); ConnectionId=[guid]::NewGuid().ToString(); Missing=0
                    }
                    $connections[$key]=$record
                    $kind=if ($baseline) {'CONNECTION_PRESENT'} else {'CONNECTION_OBSERVED'}
                    Add-Observation $record $kind $sampleTime.ToString('o')
                } else {
                    $record=$connections[$key]; $record.Missing=0
                    if ($record.State -ne $socket.State.ToString()) {
                        $record.State=$socket.State.ToString()
                        Add-Observation $record 'CONNECTION_STATE_CHANGED' $sampleTime.ToString('o')
                    }
                }
            }
            foreach ($key in @($connections.Keys)) {
                if (!$seen.ContainsKey($key)) {
                    $connections[$key].Missing++
                    if ($connections[$key].Missing -ge $MissingSamples) {
                        Add-Observation $connections[$key] 'CONNECTION_NO_LONGER_OBSERVED' $sampleTime.ToString('o')
                        $connections.Remove($key)
                    }
                }
            }
            $baseline=$false; $lastSample=$sampleTime; $sampleError=$null
        } catch {
            if (!$baseline) { $gapCount++ }
            $baseline=$true; $sampleError='SAMPLE_FAILED'
            # Keep queued genuine observations. Never infer disappearance from a failed sample.
        }
        $now=[DateTimeOffset]::UtcNow
        if ($registered -and $script:queue.Count -gt 0 -and $now -ge $retryAt) {
            $batch=@($script:queue.ToArray() | Select-Object -First 100)
            if (Send-CollectorRequest 'observations' @{collectorId=$CollectorId;sessionId=$sessionId;observations=$batch}) {
                for ($i=0; $i -lt $batch.Count; $i++) { $null=$script:queue.Dequeue() }
                $retryDelay=2; $retryAt=$now; $deliveryError=$null
            } else { $deliveryError='INGESTION_FAILED'; $retryAt=$now.AddSeconds($retryDelay); $retryDelay=[Math]::Min(30,$retryDelay*2) }
        }
        if ($registered -and $now -ge $heartbeatAt) {
            $lastSampleText=if ($lastSample) {$lastSample.ToString('o')} else {$null}
            $ok=Send-CollectorRequest 'heartbeat' @{
                collectorId=$CollectorId;sessionId=$sessionId;lastSuccessfulSampleAt=$lastSampleText
                sampleError=$sampleError;deliveryError=$deliveryError;queuedEvents=$script:queue.Count;droppedEvents=$script:dropped;gapCount=$gapCount
            }
            if (!$ok) { Write-Warning 'Collector heartbeat delivery failed; backend health will become unavailable.' }
            $heartbeatAt=$now.AddSeconds(5)
        }
        $remaining=$SampleIntervalMs-[int]$cycle.ElapsedMilliseconds
        if ($remaining -gt 0) { Start-Sleep -Milliseconds $remaining }
    }
} finally {
    Write-Host "Observer stopped. Unsent observations: $($script:queue.Count); overflow drops: $script:dropped. Restart creates a new baseline."
}
