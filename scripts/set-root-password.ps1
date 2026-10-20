# ============================================================
# docqa - set / change the MySQL root password
#
# Run in a NORMAL PowerShell (no administrator needed).
# It asks for the password twice, never echoes it, and never
# writes it into the console, the project, or git.
# ============================================================

$ErrorActionPreference = 'Stop'

function Info($m) { Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[ OK ] $m" -ForegroundColor Green }
function Bad($m)  { Write-Host "[FAIL] $m" -ForegroundColor Red }

$mysql = 'D:\MySQL\MySQL Server 8.4\bin\mysql.exe'
if (-not (Test-Path $mysql)) {
    Bad "mysql.exe not found at $mysql"
    exit 1
}

# ---------- 1. ask for the password twice ----------
Write-Host 'Type the NEW root password (input is hidden), then press Enter:' -ForegroundColor Yellow
$s1 = Read-Host -AsSecureString
Write-Host 'Type it again:' -ForegroundColor Yellow
$s2 = Read-Host -AsSecureString

$b1 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s1)
$b2 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s2)
try {
    $p1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b1)
    $p2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b2)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b1)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b2)
}

if ([string]::IsNullOrWhiteSpace($p1)) {
    Bad 'password must not be empty'
    exit 1
}
if ($p1 -cne $p2) {
    Bad 'the two passwords are not the same, run the script again'
    exit 1
}

# ---------- 2. try the current (empty) password first ----------
function RunMysql {
    param([string[]]$Extra)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $mysql @Extra 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $old
    }
    return @{ Out = $out; Code = $code }
}

$ping = RunMysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306', '-u', 'root', '--skip-password', '-e', 'SELECT 1;')
if ($ping.Code -ne 0) {
    Bad 'cannot log in as root with an EMPTY password.'
    Bad 'That means the password was already set (by you, earlier).'
    Bad 'If you forgot it, say so - there is a documented reset procedure.'
    exit 1
}
Info 'root currently has an empty password (expected)'

# ---------- 3. apply the new password via a temp SQL file ----------
# 用文件而不是命令行参数，避免密码出现在进程列表 / 控制台历史里
$sqlFile = Join-Path $env:TEMP ('docqa-root-' + [guid]::NewGuid().ToString('N') + '.sql')
$escaped = $p1 -replace '\\', '\\\\' -replace "'", "''"

$sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$escaped';
FLUSH PRIVILEGES;
"@
Set-Content -Path $sqlFile -Value $sql -Encoding UTF8 -NoNewline

try {
    $src = 'source ' + ($sqlFile -replace '\\', '/')
    $r = RunMysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306', '-u', 'root', '--skip-password',
                    '--default-character-set=utf8mb4', "--execute=$src")
} finally {
    Remove-Item $sqlFile -Force -ErrorAction SilentlyContinue
}

if ($r.Code -ne 0) {
    Bad "failed to set the root password (exit $($r.Code))"
    $r.Out
    exit 1
}
Ok 'root password changed'

# ---------- 4. verify: empty password must now be refused ----------
$old = RunMysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306', '-u', 'root', '--skip-password', '-e', 'SELECT 1;')
if ($old.Code -eq 0) {
    Bad 'empty password still works - the change did not take effect'
    exit 1
}
Ok 'empty password is now refused'

# ---------- 5. verify: the new password works ----------
$new = RunMysql @('--protocol=TCP', '-h', '127.0.0.1', '-P', '3306', '-u', 'root', "--password=$p1", '-e', 'SELECT VERSION() AS version;')
if ($new.Code -ne 0) {
    Bad 'the new password does not work - something is wrong, do not close the window yet'
    $new.Out
    exit 1
}
$new.Out | Out-Host
Ok 'the new password works'

Write-Host ''
Write-Host 'Done. Write the password down somewhere safe.' -ForegroundColor Yellow
Write-Host 'The application does NOT use root: it uses docqa / Docqa@2026 on database docqa.' -ForegroundColor Yellow
exit 0
