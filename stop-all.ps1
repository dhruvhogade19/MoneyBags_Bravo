$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidFile = Join-Path (Join-Path $projectRoot "logs") "moneybags-pids.json"
$servicePorts = @(
    @{ Name = "discovery-server"; Port = 8761 },
    @{ Name = "deposit-account-service"; Port = 8086 },
    @{ Name = "eod-reconciliation-service"; Port = 8091 },
    @{ Name = "api-gateway"; Port = 8080 }
)

# spring-boot:run creates a child Java process. Stop the actual port owner first.
foreach ($service in $servicePorts) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue
    foreach ($listener in @($listeners)) {
        $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        if ($null -ne $process) {
            Stop-Process -Id $process.Id -Force
            Write-Host ("Stopped " + $service.Name + " port owner (PID " + $process.Id + ", port " + $service.Port + ")")
        }
    }
}

# Also stop the Maven wrapper processes recorded by run-all.ps1.
if (Test-Path -LiteralPath $pidFile) {
    $entries = Get-Content -Raw -LiteralPath $pidFile | ConvertFrom-Json
    foreach ($entry in @($entries)) {
        $process = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
        if ($null -ne $process) {
            Stop-Process -Id $entry.Id -Force
            Write-Host ("Stopped " + $entry.Name + " launcher (PID " + $entry.Id + ")")
        }
    }
    Remove-Item -LiteralPath $pidFile -Force
}

Start-Sleep -Seconds 2
$remaining = $servicePorts | Where-Object {
    $null -ne (Get-NetTCPConnection -State Listen -LocalPort $_.Port -ErrorAction SilentlyContinue)
}
if ($remaining) {
    Write-Warning ("Some ports are still occupied: " + (($remaining.Port) -join ", "))
} else {
    Write-Host "All Moneybags service ports are stopped."
}
