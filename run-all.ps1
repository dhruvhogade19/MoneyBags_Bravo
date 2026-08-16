$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $projectRoot "logs"
$pidFile = Join-Path $logDir "moneybags-pids.json"
$envFile = Join-Path $projectRoot ".env"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line -split "=", 2
            if ($parts.Count -eq 2 -and -not [string]::IsNullOrWhiteSpace($parts[0])) {
                [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
            }
        }
    }
    Write-Host "Loaded local configuration from .env" -ForegroundColor Cyan
}

$java25 = Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $java25) {
    throw "JDK 25 was not found under C:\Program Files\Java. Install JDK 25 before starting the services."
}
$env:JAVA_HOME = $java25.FullName
# Some launch environments expose both PATH and Path. PowerShell's
# Start-Process rejects that case-insensitive duplicate while copying the
# environment, so normalize it before spawning the Maven launchers.
$inheritedPath = $env:Path
[Environment]::SetEnvironmentVariable("PATH", $null, "Process")
[Environment]::SetEnvironmentVariable(
    "Path",
    (Join-Path $java25.FullName "bin") + ";" + $inheritedPath,
    "Process"
)
if ([string]::IsNullOrWhiteSpace($env:HEALTH_SHOW_DETAILS)) {
    $env:HEALTH_SHOW_DETAILS = "always"
}
if ([string]::IsNullOrWhiteSpace($env:MAIL_HEALTH_ENABLED)) {
    # Local runs use the mock-mail profile unless an SMTP server is explicitly configured.
    $env:MAIL_HEALTH_ENABLED = "false"
}
if ([string]::IsNullOrWhiteSpace($env:M2M_CLIENT_SECRET)) {
    # Must match the identity service's local-profile client secret.
    $env:M2M_CLIENT_SECRET = "local-service-secret-change-me"
}
if ([string]::IsNullOrWhiteSpace($env:STUB_UPSTREAM_CLIENTS)) {
    # The complete local stack is available, so exercise real service integrations.
    $env:STUB_UPSTREAM_CLIENTS = "false"
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
    @{ Name = "identity-access-service"; Directory = "identity-access-service"; Port = 8093; Profiles = "local" },
    @{ Name = "cif-service"; Directory = "cif-service"; Port = 8081 },
    @{ Name = "kyc-service"; Directory = "kyc-service"; Port = 8082 },
    @{ Name = "product-master-service"; Directory = "product-master-service"; Port = 8083 },
    @{ Name = "payments-service"; Directory = "payments-service"; Port = 8085 },
    @{ Name = "deposit-account-service"; Directory = "deposit-account-service"; Port = 8086 },
    @{ Name = "credit-card-service"; Directory = "credit-card-service"; Port = 8087 },
    @{ Name = "accounting-service"; Directory = "accounting-service"; Port = 8088; Profiles = "local" },
    @{ Name = "notification-service"; Directory = "notification-service"; Port = 8090; Profiles = "mock-mail" },
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
    $arguments = "-Dmaven.test.skip=true spring-boot:run"
    if ($service.ContainsKey("Profiles")) {
        $arguments += " -Dspring-boot.run.profiles=" + $service.Profiles
    }
    $process = Start-Process -FilePath "mvn" -ArgumentList $arguments -WorkingDirectory $serviceDir `
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
Write-Host "Identity: http://localhost:8093" -ForegroundColor Green
Write-Host "Product : http://localhost:8083/swagger-ui.html" -ForegroundColor Green
Write-Host "Payments: http://localhost:8085/swagger-ui/index.html" -ForegroundColor Green
Write-Host "Accounting: http://localhost:8088/swagger-ui.html" -ForegroundColor Green
Write-Host "Eureka  : http://localhost:8761" -ForegroundColor Green
Write-Host "Gateway : http://localhost:8080" -ForegroundColor Green
Write-Host ("Logs    : " + $logDir) -ForegroundColor Green
Write-Host "Follow deposit logs: Get-Content .\logs\deposit-account-service.out.log -Wait -Tail 100"
Write-Host "Follow payments logs: Get-Content .\logs\payments-service.out.log -Wait -Tail 100"
Write-Host "Follow accounting logs: Get-Content .\logs\accounting-service.out.log -Wait -Tail 100"
