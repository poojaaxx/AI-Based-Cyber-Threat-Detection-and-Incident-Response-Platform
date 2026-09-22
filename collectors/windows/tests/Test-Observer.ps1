# Deterministic observer unit tests. No real telemetry is collected or sent to the backend.
$ErrorActionPreference='Stop'
$savedKey=$env:COLLECTOR_INGEST_KEY
$env:COLLECTOR_INGEST_KEY='test-only-key-not-used-by-any-running-backend'
function Assert-True($Condition,[string]$Message) { if (!$Condition) { throw $Message } }
function Get-NetTCPConnection {
    $global:CyberGuardObserverTest.Sample++
    $n=$global:CyberGuardObserverTest.Sample
    if ($n -eq 3) { throw 'Deterministic sample failure' }
    if ($n -in @(6,7)) { return }
    [pscustomobject]@{LocalAddress='127.0.0.1';LocalPort=51002;RemoteAddress='127.0.0.1';RemotePort=8080;OwningProcess=$PID;State='Established';CreationTime=$global:CyberGuardObserverTest.Created}
    [pscustomobject]@{LocalAddress='127.0.0.1';LocalPort=8080;RemoteAddress='127.0.0.1';RemotePort=51002;OwningProcess=0;State='Established';CreationTime=$global:CyberGuardObserverTest.Created}
    [pscustomobject]@{
        LocalAddress='127.0.0.1';LocalPort=51001;RemoteAddress='127.0.0.1';RemotePort=19090
        OwningProcess=0;CreationTime=$global:CyberGuardObserverTest.Created
        State=if ($n -eq 5) {'CloseWait'} else {'Established'}
    }
}
function Invoke-RestMethod {
    param($Method,$Uri,$Headers,$ContentType,$Body,$TimeoutSec)
    $decoded=[Text.Encoding]::UTF8.GetString($Body) | ConvertFrom-Json
    if ($Uri.EndsWith('/observations')) {
        if ($global:CyberGuardObserverTest.Reject) { throw 'Deterministic delivery failure' }
        foreach ($row in $decoded.observations) { $global:CyberGuardObserverTest.Rows.Add($row) }
    }
    if ($Uri.EndsWith('/heartbeat')) { $global:CyberGuardObserverTest.Heartbeats.Add($decoded) }
    return @{}
}
try {
    $global:CyberGuardObserverTest=@{Sample=0;Created=[DateTime]::Now;Reject=$false;Rows=[Collections.Generic.List[object]]::new();Heartbeats=[Collections.Generic.List[object]]::new()}
    & "$PSScriptRoot/../Watch-NetworkConnections.ps1" -SampleIntervalMs 500 -RunSeconds 6
    $rows=$global:CyberGuardObserverTest.Rows
    Assert-True ($rows.Count -eq 5) 'Unchanged sockets must not emit duplicate observations.'
    Assert-True (@($rows | Where-Object eventType -eq CONNECTION_PRESENT).Count -eq 2) 'Recovery must create a fresh baseline.'
    Assert-True (@($rows | Where-Object eventType -eq CONNECTION_NO_LONGER_OBSERVED).Count -eq 1) 'Only successful missing samples can remove a socket.'
    Assert-True ($rows[1].connectionId -eq $rows[2].connectionId) 'TCP state changes must preserve connection identity.'
    Assert-True ($rows[0].connectionId -ne $rows[1].connectionId) 'A sampling gap must reset observation identity.'
    Assert-True ($rows[4].eventType -eq 'CONNECTION_OBSERVED') 'Reopened socket must produce new activity.'
    $global:CyberGuardObserverTest=@{Sample=0;Created=[DateTime]::Now;Reject=$true;Rows=[Collections.Generic.List[object]]::new();Heartbeats=[Collections.Generic.List[object]]::new()}
    & "$PSScriptRoot/../Watch-NetworkConnections.ps1" -SampleIntervalMs 500 -RunSeconds 6 -BufferCapacity 2
    $health=$global:CyberGuardObserverTest.Heartbeats | Select-Object -Last 1
    Assert-True ($health.queuedEvents -le 2) 'Buffer must remain bounded.'
    Assert-True ($health.droppedEvents -gt 0) 'Overflow must be reported.'
    Assert-True ($health.deliveryError -eq 'INGESTION_FAILED') 'Delivery failure must be reported independently of sampling.'
    Write-Host 'PASS: baseline, unchanged suppression, failure recovery, state identity, successful missing samples, reconnect, bounded buffer, overflow, delivery health.'
} finally {
    $env:COLLECTOR_INGEST_KEY=$savedKey
    Remove-Variable CyberGuardObserverTest -Scope Global -ErrorAction SilentlyContinue
}
