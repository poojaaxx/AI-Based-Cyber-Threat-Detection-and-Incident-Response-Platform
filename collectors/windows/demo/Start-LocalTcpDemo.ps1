#Requires -Version 5.1
param([ValidateRange(1024,65535)][int]$Port=19090, [ValidateRange(0,86400)][int]$RunSeconds=0)
$ErrorActionPreference='Stop'
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Parse('127.0.0.1'),$Port)
$clients=New-Object 'System.Collections.Generic.List[System.Net.Sockets.TcpClient]'
$started=[DateTimeOffset]::UtcNow
try {
    $listener.Start()
    Write-Host "Demo TCP listener: 127.0.0.1:$Port. No payloads are read or stored. Ctrl+C stops."
    while ($RunSeconds -eq 0 -or ([DateTimeOffset]::UtcNow-$started).TotalSeconds -lt $RunSeconds) {
        if ($listener.Pending()) { $clients.Add($listener.AcceptTcpClient()) }
        foreach ($client in @($clients.ToArray())) {
            if ($client.Client.Poll(0,[Net.Sockets.SelectMode]::SelectRead) -and $client.Available -eq 0) {
                $client.Close(); $null=$clients.Remove($client)
            }
        }
        Start-Sleep -Milliseconds 100
    }
} finally {
    foreach ($client in $clients) { $client.Close() }
    $listener.Stop()
}
