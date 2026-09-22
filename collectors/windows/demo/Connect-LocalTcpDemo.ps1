#Requires -Version 5.1
param([ValidateRange(1024,65535)][int]$Port=19090, [ValidateRange(5,300)][int]$HoldSeconds=15)
$ErrorActionPreference='Stop'
$client=[Net.Sockets.TcpClient]::new()
try {
    $client.Connect('127.0.0.1',$Port)
    Write-Host "Actual socket: $($client.Client.LocalEndPoint) <-> $($client.Client.RemoteEndPoint); process $PID"
    Start-Sleep -Seconds $HoldSeconds
} finally { $client.Close(); Write-Host 'Demo socket closed.' }
