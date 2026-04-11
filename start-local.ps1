# ─────────────────────────────────────────────────────────────────────
# Team Blackbox — 로컬 개발 시작 스크립트
# 사용: PowerShell에서 .\start-local.ps1 실행
# ─────────────────────────────────────────────────────────────────────

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── .env.local 로드 ──────────────────────────────────────────────────
$envFile = "$Root\.env.local"
if (-not (Test-Path $envFile)) {
    Write-Host "❌ .env.local 파일이 없습니다. .env.local을 먼저 만드세요." -ForegroundColor Red
    exit 1
}

$envVars = @{}
Get-Content $envFile | Where-Object { $_ -notmatch '^\s*#' -and $_ -match '=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    $key   = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($key -and $value) { $envVars[$key] = $value }
}

# .env 파일에서 API 키 보완 (CLAUDE_API_KEY 등 비어있으면 .env에서 가져옴)
$mainEnv = "$Root\.env"
if (Test-Path $mainEnv) {
    Get-Content $mainEnv | Where-Object { $_ -notmatch '^\s*#' -and $_ -match '=' } | ForEach-Object {
        $parts = $_ -split '=', 2
        $key   = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($key -and $value -and -not $envVars.ContainsKey($key)) {
            $envVars[$key] = $value
        }
    }
}

# uploads 폴더 생성
$uploadDir = "$Root\backend\uploads"
New-Item -ItemType Directory -Force -Path $uploadDir | Out-Null
$envVars["FILE_UPLOAD_DIR"] = $uploadDir

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host " Team Blackbox — 로컬 개발 서버 시작"     -ForegroundColor White
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray

# ── 1. DB (Docker) 시작 ──────────────────────────────────────────────
Write-Host "[1/3] PostgreSQL DB 시작 중..." -ForegroundColor Cyan
$dbCompose = "$Root\docker-compose.db-only.yml"
docker compose -f $dbCompose up -d 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "     ⚠️  Docker를 찾을 수 없습니다. DB가 로컬에 설치된 경우 계속합니다." -ForegroundColor Yellow
} else {
    Write-Host "     ✅ DB 준비 중 (5초 대기)..." -ForegroundColor Green
    Start-Sleep -Seconds 5
}

# ── 2. 백엔드 (Gradle bootRun) ───────────────────────────────────────
Write-Host "[2/3] 백엔드 시작 중 (포트 8080)..." -ForegroundColor Cyan

# 환경변수를 백엔드 터미널에 주입할 명령 문자열 생성
$setEnvLines = ($envVars.GetEnumerator() | ForEach-Object {
    "`$env:$($_.Key) = '$($_.Value)'"
}) -join "; "

$backendCmd = "$setEnvLines; Set-Location '$Root\backend'; .\gradlew.bat bootRun"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCmd -WindowStyle Normal

Write-Host "     ✅ 백엔드 터미널 열림" -ForegroundColor Green

# ── 3. 프론트엔드 (npm run dev) ──────────────────────────────────────
Write-Host "[3/3] 프론트엔드 시작 중 (포트 3000)..." -ForegroundColor Cyan

$frontendCmd = "Set-Location '$Root\frontend'; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCmd -WindowStyle Normal

Write-Host "     ✅ 프론트엔드 터미널 열림" -ForegroundColor Green

# ── 완료 ─────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host " 서버 시작 완료 (백엔드 초기화에 30~60초 소요)" -ForegroundColor White
Write-Host ""
Write-Host "  🌐 프론트엔드:  http://localhost:3000" -ForegroundColor Blue
Write-Host "  ⚙️  백엔드 API:  http://localhost:8080/api/health" -ForegroundColor Green
Write-Host "  🗄️  DB:         localhost:5432 / blackbox_db" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host ""
