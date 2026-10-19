param([string]$Java='java', [int]$IntegrationPort=18080, [int]$TargetPort=18081, [switch]$BrowserTests)
$ErrorActionPreference='Stop'
if (-not $env:REPLAY_KEY) { throw 'Set a stable REPLAY_KEY for the disposable test stack.' }
if (-not $env:DB_PASSWORD -or -not $env:DB_URL) { throw 'Set DB_PASSWORD and DB_URL for a disposable test database first.' }
$root=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $root '.tools'
New-Item -ItemType Directory -Force $runtime | Out-Null
$targetProcess=$null
$integrationProcess=$null
$originalProxy=$env:API_PROXY_TARGET
$originalBase=$env:E2E_BASE_URL
$originalDatabase=$env:E2E_DB_URL
function Wait-Healthy([string]$Url, [Diagnostics.Process]$Process) {
    for ($i=0; $i -lt 60; $i++) {
        if ($Process.HasExited) { throw "Service exited before becoming healthy: $Url" }
        try { if ((Invoke-RestMethod "$Url/actuator/health" -TimeoutSec 2).status -eq 'UP') { return } } catch {}
        Start-Sleep -Milliseconds 500
    }
    throw "Service did not become healthy: $Url. Inspect .tools/smoke-*.log."
}
try {
    foreach ($port in @($IntegrationPort, $TargetPort)) {
        $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
        try { $listener.Start() } finally { $listener.Stop() }
    }
    $targetJar='"'+(Join-Path $root 'backend/target-simulator/target/target-simulator-0.1.0-SNAPSHOT.jar')+'"'
    $integrationJar='"'+(Join-Path $root 'backend/integration-server/target/integration-server-0.1.0-SNAPSHOT.jar')+'"'
    $targetProcess=Start-Process -FilePath $Java -ArgumentList @('-jar',$targetJar,"--server.port=$TargetPort",'--server.address=127.0.0.1') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/smoke-target.log" -RedirectStandardError "$runtime/smoke-target-error.log"
    Wait-Healthy "http://127.0.0.1:$TargetPort" $targetProcess
    $integrationProcess=Start-Process -FilePath $Java -ArgumentList @('-jar',$integrationJar,"--server.port=$IntegrationPort",'--server.address=127.0.0.1',"--lab.target-url=http://127.0.0.1:$TargetPort") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/smoke-integration.log" -RedirectStandardError "$runtime/smoke-integration-error.log"
    $base="http://127.0.0.1:$IntegrationPort"
    Wait-Healthy $base $integrationProcess
    $trace=[guid]::NewGuid().ToString('N')
    $request='SMOKE-'+[guid]::NewGuid().ToString('N')
    $body=@{interfaceId='IF-USER-001';requestId=$request;payload=@{userId='USER001';userName='Demo'}} | ConvertTo-Json
    $response=Invoke-WebRequest "$base/api/requests" -Method Post -ContentType 'application/json' -Headers @{'X-Trace-Id'=$trace} -Body $body
    $result=$response.Content | ConvertFrom-Json
    if ($result.status -ne 'SUCCESS' -or $result.traceId -ne $trace -or $result.response.traceId -ne $trace -or $result.requestId -ne $request) { throw "Unexpected response: $($response.Content)" }
    if (-not $result.attemptId -or $response.Headers['X-Trace-Id'] -ne $trace) { throw 'Missing identifiers' }
    $api=Invoke-RestMethod "$base/v3/api-docs"
    if (-not $api.paths.'/api/requests') { throw 'OpenAPI request endpoint is missing' }
    $result | ConvertTo-Json -Depth 8
    Write-Output 'PASS: two independent JVMs, PostgreSQL health, HTTP round trip, trace propagation, OpenAPI'
    if ($BrowserTests) {
        $env:API_PROXY_TARGET=$base
        $env:E2E_BASE_URL=$null
        $env:E2E_DB_URL=$env:DB_URL -replace '^jdbc:', ''
        Push-Location (Join-Path $root 'frontend')
        try {
            & npm.cmd run test:e2e
            if ($LASTEXITCODE -ne 0) { throw 'Browser tests failed. Inspect frontend/playwright-report.' }
        } finally { Pop-Location }
    }
} finally {
    $env:API_PROXY_TARGET=$originalProxy
    $env:E2E_BASE_URL=$originalBase
    $env:E2E_DB_URL=$originalDatabase
    if ($integrationProcess -and -not $integrationProcess.HasExited) { Stop-Process -Id $integrationProcess.Id }
    if ($targetProcess -and -not $targetProcess.HasExited) { Stop-Process -Id $targetProcess.Id }
}
