$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$runAll = Join-Path $projectRoot "run-all.ps1"
$stopAll = Join-Path $projectRoot "stop-all.ps1"
$verifier = Join-Path $PSScriptRoot "verify-authenticated-workflows.mjs"
$authenticationAudit = Join-Path $PSScriptRoot "audit-api-authentication.mjs"

try {
    & $runAll
    if ($LASTEXITCODE -ne 0) {
        throw "Moneybags stack startup failed with exit code $LASTEXITCODE"
    }

    & node $verifier
    if ($LASTEXITCODE -ne 0) {
        throw "Live authenticated workflow verification failed with exit code $LASTEXITCODE"
    }

    & node $authenticationAudit
    if ($LASTEXITCODE -ne 0) {
        throw "API-wide authentication audit failed with exit code $LASTEXITCODE"
    }
} finally {
    & $stopAll
}
