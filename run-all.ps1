$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $projectRoot "logs"
$pidFile = Join-Path $logDir "moneybags-pids.json"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$java25 = Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $java25) {
    throw "JDK 25 was not found under C:\Program Files\Java. Install JDK 25 before starting the services."
}
$env:JAVA_HOME = $java25.FullName
$env:Path = (Join-Path $java25.FullName "bin") + ";" + $env:Path
if ([string]::IsNullOrWhiteSpace($env:HEALTH_SHOW_DETAILS)) {
    $env:HEALTH_SHOW_DETAILS = "always"
}
$javaExecutable = Join-Path $java25.FullName "bin\java.exe"
$javaOutput = & $javaExecutable --version
$javaExitCode = $LASTEXITCODE
if ($javaExitCode -ne 0) {
    throw "Unable to run JDK 25 from $javaExecutable"
}
$javaVersion = $javaOutput | Select-Object -First 1
Write-Host ("Using Java: " + $javaVersion) -ForegroundColor Cyan

$services = @(
    @{ Name = "discovery-server"; Directory = "discovery-server"; Port = 8761 },
    @{ Name = "deposit-account-service"; Directory = "deposit-account-service"; Port = 8086 },
    @{ Name = "api-gateway"; Directory = "api-gateway"; Port = 8080 }
)

$occupied = foreach ($service in $services) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $listener) {
        [pscustomobject]@{ Service = $service.Name; Port = $service.Port; PID = $listener.OwningProcess }
    }
}
if ($occupied) {
    Write-Host "Cannot start: Moneybags ports are already occupied." -ForegroundColor Red
    $occupied | Format-Table -AutoSize
    throw "Run .\stop-all.ps1 first, then run .\run-all.ps1 again."
}

$processes = @()
foreach ($service in $services) {
    $serviceDir = Join-Path $projectRoot $service.Directory
    $stdout = Join-Path $logDir ($service.Name + ".out.log")
    $stderr = Join-Path $logDir ($service.Name + ".err.log")
    $process = Start-Process -FilePath "mvn" -ArgumentList "-Dmaven.test.skip=true spring-boot:run" -WorkingDirectory $serviceDir `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    $processes += [pscustomobject]@{ Name = $service.Name; Id = $process.Id; Port = $service.Port }
    Start-Sleep -Seconds 2
}

$processes | ConvertTo-Json | Set-Content -LiteralPath $pidFile
Write-Host "Waiting for services to open their ports..." -ForegroundColor Cyan
# Oracle connectivity, Liquibase and Hibernate schema validation can take more
# than one minute on the shared database. Avoid reporting a healthy service as
# NOT STARTED while it is still completing startup validation.
$startupDeadline = (Get-Date).AddSeconds(180)
do {
    $waitingFor = @($services | Where-Object {
        $null -eq (Get-NetTCPConnection -State Listen -LocalPort $_.Port -ErrorAction SilentlyContinue |
            Select-Object -First 1)
    })
    if ($waitingFor.Count -eq 0) {
        break
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $startupDeadline)

$status = foreach ($service in $services) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    $listening = $null -ne $listener
    $healthStatus = $null
    if ($listening) {
        try {
            $healthResponse = Invoke-WebRequest -UseBasicParsing -Uri ("http://localhost:" + $service.Port + "/actuator/health") -TimeoutSec 20
            $healthStatus = [int]$healthResponse.StatusCode
        } catch {
            if ($null -ne $_.Exception.Response) {
                $healthStatus = [int]$_.Exception.Response.StatusCode
            }
        }
    }
    $displayStatus = if (-not $listening) {
        "NOT STARTED - CHECK LOG"
    } elseif ($healthStatus -eq 200) {
        "UP"
    } elseif ($healthStatus -eq 503) {
        "STARTED / HEALTH DOWN"
    } else {
        "STARTED / HEALTH UNKNOWN"
    }
    [pscustomobject]@{
        Service = $service.Name
        Port = $service.Port
        PID = if ($listening) { $listener.OwningProcess } else { $null }
        Status = $displayStatus
        Log = Join-Path $logDir ($service.Name + ".out.log")
    }
}
$status | Format-Table -AutoSize
Write-Host "Swagger : http://localhost:8086/swagger-ui.html" -ForegroundColor Green
Write-Host "Eureka  : http://localhost:8761" -ForegroundColor Green
Write-Host "Gateway : http://localhost:8080" -ForegroundColor Green
Write-Host ("Logs    : " + $logDir) -ForegroundColor Green
Write-Host "Follow deposit logs: Get-Content .\logs\deposit-account-service.out.log -Wait -Tail 100"
