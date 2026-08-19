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
# Maven derives its local repository from Java's user.home. Some restricted
# launch environments report that property as C:\ even when USERPROFILE is
# correct, causing every service launcher to fail against C:\.m2. Pin it to the
# signed-in Windows profile while preserving any other MAVEN_OPTS.
if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE) -and
    ($env:MAVEN_OPTS -notmatch '(?:^|\s)-Duser\.home=')) {
    $userHomeOption = '-Duser.home="' + $env:USERPROFILE + '"'
    $env:MAVEN_OPTS = (($env:MAVEN_OPTS, $userHomeOption) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ' '
}
# Keep Spring Boot's generated servlet/session directories inside a leaf owned
# by the current Windows identity. A shared directory can contain Spring temp
# folders created by VS Code, Codex, or the signed-in user; Spring Boot 4 then
# correctly rejects a folder owned by a different principal.
$runtimeIdentity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value -replace '[^A-Za-z0-9.-]', '_'
$runtimeTempDir = Join-Path $projectRoot (".runtime-tmp\" + $runtimeIdentity)
New-Item -ItemType Directory -Force -Path $runtimeTempDir | Out-Null
if ($env:JAVA_TOOL_OPTIONS -notmatch '(?:^|\s)-Djava\.io\.tmpdir=') {
    $javaTempOption = '-Djava.io.tmpdir="' + $runtimeTempDir + '"'
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS, $javaTempOption) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ' '
}
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
# A developer database commonly has a much smaller Oracle process/session limit
# than production. Ten independent default Hikari pools can otherwise exhaust
# the listener before the last service starts (ORA-12516).
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE)) {
    $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "3"
}
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE)) {
    $env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = "1"
}
# A VPN reconnect can change the machine's preferred IP address while old Eureka
# registrations are still alive. Local services all run on this machine, so a
# stable localhost registration prevents the Gateway from routing to a stale VPN IP.
if ([string]::IsNullOrWhiteSpace($env:EUREKA_INSTANCE_HOSTNAME)) {
    $env:EUREKA_INSTANCE_HOSTNAME = "localhost"
}
if ([string]::IsNullOrWhiteSpace($env:EUREKA_INSTANCE_PREFER_IP_ADDRESS)) {
    $env:EUREKA_INSTANCE_PREFER_IP_ADDRESS = "false"
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
    @{ Name = "cif-service"; Directory = "cif-service"; Port = 8081; DatabaseBacked = $true },
    @{ Name = "kyc-service"; Directory = "kyc-service"; Port = 8082; DatabaseBacked = $true },
    @{ Name = "product-master-service"; Directory = "product-master-service"; Port = 8083; DatabaseBacked = $true },
    @{ Name = "payments-service"; Directory = "payments-service"; Port = 8085; DatabaseBacked = $true },
    @{ Name = "deposit-account-service"; Directory = "deposit-account-service"; Port = 8086; DatabaseBacked = $true },
    @{ Name = "credit-card-service"; Directory = "credit-card-service"; Port = 8084; DatabaseBacked = $true },
    # The shared Oracle schema contains a legacy Accounting data model.  Until its
    # dedicated conversion migration is applied, use the self-contained demo profile.
    @{ Name = "accounting-service"; Directory = "accounting-service"; Port = 8088; Profiles = "local" },
    @{ Name = "notification-service"; Directory = "notification-service"; Port = 8090; Profiles = "mock-mail"; DatabaseBacked = $true },
    @{ Name = "bill-generation-service"; Directory = "bill-generation-service"; Port = 8087; DatabaseBacked = $true },
    @{ Name = "statements-service"; Directory = "statements-service"; Port = 8089; Profiles = "local"; DatabaseBacked = $true },
    @{ Name = "api-gateway"; Directory = "api-gateway"; Port = 8080 },
    @{ Name = "moneybags-web"; Directory = "moneybags-web"; Port = 8000; Command = "npm.cmd"; Arguments = "run serve:stack"; HealthPath = "/" }
)

function Test-MoneybagsPort {
    param(
        [int]$Port,
        [int]$TimeoutMilliseconds = 250
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync("127.0.0.1", $Port)
        if (-not $connection.Wait($TimeoutMilliseconds)) {
            return $false
        }
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-MoneybagsPortOwnerIds {
    param([int]$Port)

    $ownerIds = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    if ($ownerIds.Count -gt 0) {
        return $ownerIds
    }

    # CIM listener queries can return no rows in non-elevated shells. netstat
    # is a reliable fallback for diagnostics and cleanup metadata.
    $portPattern = ":" + $Port + "\s+.*LISTENING\s+(\d+)\s*$"
    return @(netstat -ano -p tcp | ForEach-Object {
        if ($_ -match $portPattern) { [int]$Matches[1] }
    } | Select-Object -Unique)
}

$occupied = foreach ($service in $services) {
    if (Test-MoneybagsPort -Port $service.Port) {
        $ownerId = @(Get-MoneybagsPortOwnerIds -Port $service.Port | Select-Object -First 1)
        [pscustomobject]@{ Service = $service.Name; Port = $service.Port; PID = $ownerId }
    }
}
if ($occupied) {
    Write-Host "Cannot start: Moneybags ports are already occupied." -ForegroundColor Red
    $occupied | Format-Table -AutoSize
    throw "Run .\stop-all.ps1 first, then run .\run-all.ps1 again."
}

$processes = @()
# Discovery and Identity are the only bootstrap dependencies. Start them
# together, wait once for their ports, then launch the complete application
# tier concurrently. This avoids serializing Maven, Liquibase and Hikari startup
# for every database-backed service.
$bootstrapStartupSeconds = if ([string]::IsNullOrWhiteSpace($env:MONEYBAGS_BOOTSTRAP_WAIT_SECONDS)) {
    60
} else {
    [Math]::Max(5, [int]$env:MONEYBAGS_BOOTSTRAP_WAIT_SECONDS)
}
$finalStartupSeconds = if ([string]::IsNullOrWhiteSpace($env:MONEYBAGS_STARTUP_WAIT_SECONDS)) {
    180
} else {
    [Math]::Max(10, [int]$env:MONEYBAGS_STARTUP_WAIT_SECONDS)
}

function Start-MoneybagsService {
    param([hashtable]$Service)

    $serviceDir = Join-Path $projectRoot $service.Directory
    $stdout = Join-Path $logDir ($service.Name + ".out.log")
    $stderr = Join-Path $logDir ($service.Name + ".err.log")
    $filePath = "mvn"
    $arguments = "-Dmaven.test.skip=true spring-boot:run"
    if ($service.ContainsKey("Command")) {
        $filePath = $service.Command
        $arguments = $service.Arguments
    } elseif ($service.ContainsKey("Profiles")) {
        $arguments += " -Dspring-boot.run.profiles=" + $service.Profiles
    }
    $process = Start-Process -FilePath $filePath -ArgumentList $arguments -WorkingDirectory $serviceDir `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    return [pscustomobject]@{ Name = $service.Name; Id = $process.Id; Port = $service.Port }
}

function Wait-MoneybagsPorts {
    param(
        [array]$ServicesToWaitFor,
        [int]$TimeoutSeconds,
        [string]$Message
    )

    Write-Host $Message -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $waitingFor = @($ServicesToWaitFor | Where-Object {
            -not (Test-MoneybagsPort -Port $_.Port)
        })
        if ($waitingFor.Count -eq 0) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    Write-Warning ("Startup wait expired for: " + ($waitingFor.Name -join ", "))
    return $false
}

function Test-MoneybagsTransientOracleFailure {
    param([hashtable]$Service)

    $stdout = Join-Path $logDir ($Service.Name + ".out.log")
    if (-not (Test-Path -LiteralPath $stdout)) {
        return $false
    }

    # These listener/network errors are safe to retry. Configuration, schema,
    # and application failures are deliberately not retried.
    $recentOutput = Get-Content -LiteralPath $stdout -Tail 120 -ErrorAction SilentlyContinue
    return $recentOutput -match 'ORA-(12516|12519|12514|12170|17002|17800)'
}

function Save-MoneybagsProcessIds {
    $processes | ConvertTo-Json | Set-Content -LiteralPath $pidFile
}

$bootstrapServices = @($services | Where-Object {
    $_.Name -in @("discovery-server", "identity-access-service")
})
$applicationServices = @($services | Where-Object {
    $_.Name -notin @("discovery-server", "identity-access-service")
})
$databaseServices = @($applicationServices | Where-Object { $_.DatabaseBacked })
$nonDatabaseServices = @($applicationServices | Where-Object { -not $_.DatabaseBacked })

$databaseBatchSize = if ([string]::IsNullOrWhiteSpace($env:MONEYBAGS_DATABASE_BATCH_SIZE)) {
    2
} else {
    [Math]::Max(1, [int]$env:MONEYBAGS_DATABASE_BATCH_SIZE)
}
$databaseBatchWaitSeconds = if ([string]::IsNullOrWhiteSpace($env:MONEYBAGS_DATABASE_BATCH_WAIT_SECONDS)) {
    90
} else {
    [Math]::Max(20, [int]$env:MONEYBAGS_DATABASE_BATCH_WAIT_SECONDS)
}
$oracleRetryAttempts = if ([string]::IsNullOrWhiteSpace($env:MONEYBAGS_ORACLE_RETRY_ATTEMPTS)) {
    2
} else {
    [Math]::Max(0, [int]$env:MONEYBAGS_ORACLE_RETRY_ATTEMPTS)
}

foreach ($service in $bootstrapServices) {
    $processes += Start-MoneybagsService -Service $service
}
Save-MoneybagsProcessIds

$null = Wait-MoneybagsPorts -ServicesToWaitFor $bootstrapServices `
    -TimeoutSeconds $bootstrapStartupSeconds `
    -Message "Starting Discovery and Identity concurrently..."

for ($offset = 0; $offset -lt $databaseServices.Count; $offset += $databaseBatchSize) {
    $lastIndex = [Math]::Min($offset + $databaseBatchSize - 1, $databaseServices.Count - 1)
    $batch = @($databaseServices[$offset..$lastIndex])
    foreach ($service in $batch) {
        $processes += Start-MoneybagsService -Service $service
    }
    Save-MoneybagsProcessIds

    $batchNumber = [int]($offset / $databaseBatchSize) + 1
    $null = Wait-MoneybagsPorts -ServicesToWaitFor $batch `
        -TimeoutSeconds $databaseBatchWaitSeconds `
        -Message ("Starting database service batch " + $batchNumber + " of " + [Math]::Ceiling($databaseServices.Count / $databaseBatchSize) + "...")

    $retryCandidates = @($batch | Where-Object {
        -not (Test-MoneybagsPort -Port $_.Port) -and (Test-MoneybagsTransientOracleFailure -Service $_)
    })
    for ($attempt = 1; $attempt -le $oracleRetryAttempts -and $retryCandidates.Count -gt 0; $attempt++) {
        Write-Warning ("Retrying transient Oracle connection failures (attempt " + $attempt + "): " + ($retryCandidates.Name -join ", "))
        Start-Sleep -Seconds (5 * $attempt)
        foreach ($service in $retryCandidates) {
            $processes += Start-MoneybagsService -Service $service
        }
        Save-MoneybagsProcessIds
        $null = Wait-MoneybagsPorts -ServicesToWaitFor $retryCandidates `
            -TimeoutSeconds $databaseBatchWaitSeconds `
            -Message "Waiting for Oracle retry batch..."
        $retryCandidates = @($retryCandidates | Where-Object {
            -not (Test-MoneybagsPort -Port $_.Port) -and (Test-MoneybagsTransientOracleFailure -Service $_)
        })
    }
}

foreach ($service in $nonDatabaseServices) {
    $processes += Start-MoneybagsService -Service $service
}
Save-MoneybagsProcessIds

Write-Host ("Launched " + $databaseServices.Count + " database services in batches of " + $databaseBatchSize + "; launched " + $nonDatabaseServices.Count + " non-database services concurrently.") -ForegroundColor Cyan
$null = Wait-MoneybagsPorts -ServicesToWaitFor $services `
    -TimeoutSeconds $finalStartupSeconds `
    -Message "Waiting for all service ports..."

$healthTimeoutSeconds = if ([string]::IsNullOrWhiteSpace($env:MONEYBAGS_HEALTH_TIMEOUT_SECONDS)) {
    5
} else {
    [Math]::Max(1, [int]$env:MONEYBAGS_HEALTH_TIMEOUT_SECONDS)
}

$status = foreach ($service in $services) {
    $listening = Test-MoneybagsPort -Port $service.Port
    $ownerId = if ($listening) {
        @(Get-MoneybagsPortOwnerIds -Port $service.Port | Select-Object -First 1)
    } else {
        $null
    }
    $healthStatus = $null
    if ($listening) {
        try {
            $healthPath = if ($service.ContainsKey("HealthPath")) { $service.HealthPath } else { "/actuator/health" }
            $healthResponse = Invoke-WebRequest -UseBasicParsing -Uri ("http://localhost:" + $service.Port + $healthPath) -TimeoutSec $healthTimeoutSeconds
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
        PID = $ownerId
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
Write-Host "Frontend: http://localhost:8000" -ForegroundColor Green
Write-Host ("Logs    : " + $logDir) -ForegroundColor Green
Write-Host "Follow deposit logs: Get-Content .\logs\deposit-account-service.out.log -Wait -Tail 100"
Write-Host "Follow payments logs: Get-Content .\logs\payments-service.out.log -Wait -Tail 100"
Write-Host "Follow accounting logs: Get-Content .\logs\accounting-service.out.log -Wait -Tail 100"

$failedServices = @($status | Where-Object { $_.Status -ne "UP" })
if ($failedServices.Count -gt 0) {
    $names = ($failedServices.Service -join ", ")
    throw ("MoneyBags startup was incomplete. Check logs for: " + $names)
}
