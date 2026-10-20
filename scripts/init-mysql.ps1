# ============================================================
# docqa - local MySQL 8.4 setup script  (v2)
# Run this in an ELEVATED PowerShell (Run as administrator).
#
# Idempotent: safe to re-run. Steps already done are skipped.
#   1. create D:\MySQL\data
#   2. write D:\MySQL\my.ini
#   3. initialize data directory (root gets an EMPTY password)
#   4. register + start Windows service MySQL84 (auto start)
#   5. create database `docqa` and application user `docqa`
# ============================================================

$ErrorActionPreference = 'Stop'

function Info($m) { Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[ OK ] $m" -ForegroundColor Green }
function Bad($m)  { Write-Host "[FAIL] $m" -ForegroundColor Red }

# Run a native exe without letting its stderr abort the script.
# Returns the exit code; output goes to the console unless -Quiet.
function Exec {
    param([string]$exe, [object[]]$argv, [switch]$Quiet)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $exe @argv 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $old
    }
    if (-not $Quiet) { $out | Out-Host }
    return $code
}

# ---------- 0. elevated? ----------
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Bad 'This script must run in an elevated PowerShell (right click -> Run as administrator).'
    exit 1
}

# ---------- 1. paths ----------
$base        = 'D:\MySQL\MySQL Server 8.4'
$dataDir     = 'D:\MySQL\data'
$myIni       = 'D:\MySQL\my.ini'
$serviceName = 'MySQL84'

$mysqld     = Join-Path $base 'bin\mysqld.exe'
$mysql      = Join-Path $base 'bin\mysql.exe'
$mysqladmin = Join-Path $base 'bin\mysqladmin.exe'

if (-not (Test-Path $mysqld)) {
    Bad "mysqld.exe not found at $mysqld - check the install directory."
    exit 1
}

# ---------- 2. data directory ----------
if (-not (Test-Path $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    Ok "created $dataDir"
} else {
    Info "$dataDir already exists"
}

# ---------- 3. my.ini ----------
$ini = @'
[mysqld]
port=3306
basedir=D:/MySQL/MySQL Server 8.4
datadir=D:/MySQL/data
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
default-storage-engine=INNODB
max_connections=100
innodb_buffer_pool_size=256M
default-time-zone=+08:00
log-error=D:/MySQL/data/mysql-error.log

[client]
port=3306
default-character-set=utf8mb4
'@
Set-Content -Path $myIni -Value $ini -Encoding ASCII
Ok "wrote $myIni"

# ---------- 4. initialize data dir (only once) ----------
$systemTableDir = Join-Path $dataDir 'mysql'
if (Test-Path $systemTableDir) {
    Info 'data directory already initialized, skip initialize'
} else {
    Info 'initializing data directory, this takes 10-30 seconds ...'
    $code = Exec $mysqld @("--defaults-file=$myIni", '--initialize-insecure', '--console')
    if ($code -ne 0) {
        Bad "mysqld --initialize-insecure failed with exit code $code"
        Bad "see $dataDir\mysql-error.log"
        exit 1
    }
    Ok 'data directory initialized (root password is EMPTY for now)'
}

# ---------- 5. Windows service ----------
$svc = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if (-not $svc) {
    Info "registering service $serviceName ..."
    $code = Exec $mysqld @('--install', $serviceName, "--defaults-file=$myIni")
    if ($code -ne 0) {
        Bad "mysqld --install failed with exit code $code"
        exit 1
    }
    Set-Service -Name $serviceName -StartupType Automatic
    Ok "service $serviceName registered (startup: Automatic)"
} else {
    Info "service $serviceName already exists"
}

$svc = Get-Service -Name $serviceName
if ($svc.Status -ne 'Running') {
    Info "starting service $serviceName ..."
    Start-Service -Name $serviceName
}
Ok "service $serviceName is running"

# ---------- 6. wait until the server accepts connections ----------
$alive = $false
for ($i = 1; $i -le 30; $i++) {
    $code = Exec $mysqladmin @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306',
                               '-u', 'root', '--skip-password', 'ping') -Quiet
    if ($code -eq 0) { $alive = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $alive) {
    Bad 'server did not answer on 127.0.0.1:3306 within 30s'
    Bad "see $dataDir\mysql-error.log"
    exit 1
}
Ok 'server answers on 127.0.0.1:3306'

# ---------- 7. database + application user ----------
$appUser = 'docqa'
$appPass = 'Docqa@2026'

$sql = @"
CREATE DATABASE IF NOT EXISTS docqa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$appUser'@'%' IDENTIFIED BY '$appPass';
ALTER USER '$appUser'@'%' IDENTIFIED BY '$appPass';
GRANT ALL PRIVILEGES ON docqa.* TO '$appUser'@'%';
FLUSH PRIVILEGES;
"@

$sqlFile = Join-Path $env:TEMP 'docqa-init.sql'
Set-Content -Path $sqlFile -Value $sql -Encoding ASCII

$old = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $mysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306',
               '-u', 'root', '--skip-password', '--default-character-set=utf8mb4') `
             --execute="source $($sqlFile -replace '\\','/')" 2>&1 | Out-Host
    $code = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $old
}
Remove-Item $sqlFile -Force -ErrorAction SilentlyContinue

if ($code -ne 0) {
    Bad "creating database/user failed with exit code $code"
    exit 1
}
Ok "database docqa ready, application user: $appUser / $appPass"

# ---------- 8. verify the application user can log in ----------
$code = Exec $mysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306',
                      '-u', $appUser, "--password=$appPass", '-e', 'SELECT CURRENT_USER() AS who, DATABASE() AS db;') -Quiet
if ($code -ne 0) {
    Bad 'application user docqa cannot connect - check grants'
    exit 1
}
Ok 'application user docqa can connect'

# ---------- 9. summary ----------
Write-Host ''
Write-Host '===================== SUMMARY =====================' -ForegroundColor Yellow
$null = Exec $mysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306', '-u', 'root', '--skip-password',
                      '-e', 'SELECT VERSION() AS mysql_version; SHOW DATABASES; SELECT user, host FROM mysql.user WHERE user IN (''root'',''docqa'');')
Write-Host '===================================================' -ForegroundColor Yellow
Write-Host ''
Write-Host 'NEXT STEP - set the root password yourself (nobody else should know it):' -ForegroundColor Yellow
Write-Host "  & '$mysql' -u root --skip-password" -ForegroundColor White
Write-Host "  ALTER USER 'root'@'localhost' IDENTIFIED BY 'YourOwnPassword';" -ForegroundColor White
Write-Host '  exit' -ForegroundColor White
exit 0
