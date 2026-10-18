param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$outputRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot '../outputs/expo-20261016'))
$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$jarPath = Join-Path $backendRoot 'build/libs/blackbox-0.0.1-SNAPSHOT.jar'
$frontendServer = Join-Path $frontendRoot '.next/standalone/teamplay-ui/frontend/server.js'
foreach ($artifact in @($jarPath, $frontendServer)) {
    if (-not (Test-Path -LiteralPath $artifact)) { throw "Missing verified artifact: $artifact. Follow the C-59 build instructions first." }
}
function Test-LocalPort([string]$Address, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try { return $client.ConnectAsync($Address, $Port).Wait(1000) -and $client.Connected }
    catch { return $false }
    finally { $client.Dispose() }
}
$dbState = docker inspect --format '{{.State.Running}}' blackbox_test_db 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Check Docker Desktop and blackbox_test_db. Do not substitute the development DB.' }
if ($dbState -ne 'true') { docker start blackbox_test_db | Out-Null }
if (-not (Test-LocalPort '127.0.0.1' 25432)) { throw 'Test DB port 25432 is not ready. Retry after startup.' }
if (-not (Test-LocalPort '127.0.0.1' 18083)) {
    $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:25432/bb_expo_20261016_demo'
    $env:SPRING_DATASOURCE_USERNAME = 'blackbox_test'
    $env:SPRING_DATASOURCE_PASSWORD = 'blackbox_test'
    $env:JWT_SECRET = 'test-only-dummy-signing-key-not-a-real-secret-0123456789'
    $env:SERVER_PORT = '18083'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:FILE_UPLOAD_DIR = Join-Path $outputRoot 'uploads'
    $env:LOGGING_LEVEL_ROOT = 'INFO'
    $env:DEBUG = 'false'
    Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', $jarPath) -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $outputRoot 'backend-start.log') -RedirectStandardError (Join-Path $outputRoot 'backend-error.log') | Out-Null
}
if (-not (Test-LocalPort '127.0.0.3' 13000)) {
    $env:HOSTNAME = '127.0.0.3'
    $env:PORT = '13000'
    Start-Process -FilePath (Get-Command node).Source -ArgumentList @($frontendServer) -WorkingDirectory $frontendRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $outputRoot 'frontend-start.log') -RedirectStandardError (Join-Path $outputRoot 'frontend-error.log') | Out-Null
}
Write-Output 'Local expo URL: http://127.0.0.3:13000'
Write-Output 'Initial startup may take a few seconds. No data deletion, rebuild, or external deployment.'
Write-Output "Synthetic demo accounts: $(Join-Path $outputRoot 'demo-access.json')"
