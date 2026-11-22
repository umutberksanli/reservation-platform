# Manages a separate, project-only local MySQL server for development.
# It reuses the installed MySQL binaries but has its own data folder and port,
# so the main MySQL server (port 3306), its data and its root password are untouched.
# No administrator rights are needed.
#
# Usage (from the project root):
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 init     # create + start (re-run safe)
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 start    # e.g. after a reboot
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 stop
#   powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 status
#
# Secrets: passwords are generated randomly and stored only as Windows *User*
# environment variables (DB_PASSWORD, RESERVATION_MYSQL_ROOT_PASSWORD, JWT_SECRET).
# They are never printed and never written to the repository.

param(
    [ValidateSet('init', 'start', 'stop', 'status')]
    [string]$Action = 'status'
)

$ErrorActionPreference = 'Stop'

$MysqlBin = if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { 'C:\Program Files\MySQL\MySQL Server 9.7\bin' }
$Base     = if ($env:RESERVATION_MYSQL_HOME) { $env:RESERVATION_MYSQL_HOME } else { Join-Path $env:USERPROFILE 'dev\local-mysql\reservation-platform' }
$Port     = 3307
$DbName   = 'reservation_platform'
$TestDb   = 'reservation_platform_test'
$DbUser   = 'reservation_app'
$DataDir  = Join-Path $Base 'data'
$IniFile  = Join-Path $Base 'my.ini'
$LogFile  = Join-Path $Base 'mysqld.log'
$DbUrl    = "jdbc:mysql://localhost:$Port/$DbName"
$TestUrl  = "jdbc:mysql://localhost:$Port/$TestDb"

function Get-Secret([string]$Name) {
    $v = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrEmpty($v)) { $v = [Environment]::GetEnvironmentVariable($Name, 'User') }
    return $v
}

function New-Secret([int]$Length = 28) {
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $Length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

function Initialize-Secret([string]$Name, [int]$Length = 28) {
    $v = Get-Secret $Name
    if ([string]::IsNullOrEmpty($v)) {
        $v = New-Secret $Length
        [Environment]::SetEnvironmentVariable($Name, $v, 'User')
        Write-Host "$Name generated and saved as a User environment variable (value not shown)."
    }
    else {
        Write-Host "$Name already set; reusing it (value not shown)."
    }
    return $v
}

function ConvertTo-SqlLiteral([string]$s) {
    return $s.Replace('\', '\\').Replace("'", "''")
}

function Test-ServerRunning {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

# Runs a MySQL client tool as root of the project server. The password is passed
# through a short-lived option file (not the command line), deleted right after.
function Invoke-RootClient([string]$Exe, [string[]]$ToolArgs, [string]$Sql) {
    $root = Get-Secret 'RESERVATION_MYSQL_ROOT_PASSWORD'
    if ([string]::IsNullOrEmpty($root)) { throw 'RESERVATION_MYSQL_ROOT_PASSWORD is not set. Run "init" first.' }
    $tmp = [IO.Path]::GetTempFileName()
    try {
        $opt = "[client]`r`nuser=root`r`npassword=`"$root`"`r`nhost=127.0.0.1`r`nport=$Port`r`n"
        [IO.File]::WriteAllText($tmp, $opt, [Text.Encoding]::ASCII)
        $allArgs = @("--defaults-extra-file=$tmp") + $ToolArgs
        if ($Sql) { $Sql | & (Join-Path $MysqlBin $Exe) @allArgs }
        else { & (Join-Path $MysqlBin $Exe) @allArgs }
        if ($LASTEXITCODE -ne 0) { throw "$Exe exited with code $LASTEXITCODE" }
    }
    finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Start-Server {
    if (Test-ServerRunning) { Write-Host "Project MySQL is already running on port $Port."; return }
    if (-not (Test-Path $IniFile)) { throw 'Project MySQL is not initialized. Run "init" first.' }
    Start-Process -FilePath (Join-Path $MysqlBin 'mysqld.exe') `
        -ArgumentList "--defaults-file=`"$IniFile`"" -WindowStyle Hidden | Out-Null
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-ServerRunning) { Write-Host "Project MySQL started on 127.0.0.1:$Port."; return }
        Start-Sleep -Milliseconds 500
    }
    throw "Project MySQL did not start in 30 seconds. See $LogFile"
}

# Creates the app and test databases (if missing) and grants the app user access.
# Safe to run on an existing installation.
function Initialize-Databases {
    $sql = @"
CREATE DATABASE IF NOT EXISTS $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS $TestDb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON $DbName.* TO '$DbUser'@'localhost';
GRANT ALL PRIVILEGES ON $TestDb.* TO '$DbUser'@'localhost';
"@
    Invoke-RootClient 'mysql.exe' @() $sql
    [Environment]::SetEnvironmentVariable('DB_URL', $DbUrl, 'User')
    [Environment]::SetEnvironmentVariable('DB_TEST_URL', $TestUrl, 'User')
    Write-Host "Databases '$DbName' and '$TestDb' are ready for '$DbUser'@'localhost'."
    Write-Host "DB_URL=$DbUrl"
    Write-Host "DB_TEST_URL=$TestUrl"
    # Uygulamanin JWT imza anahtari (en az 32 karakter). Yoksa uretilir; varsa dokunulmaz.
    Initialize-Secret 'JWT_SECRET' 48 | Out-Null
}

function Initialize-Server {
    if (Test-Path (Join-Path $DataDir 'mysql')) {
        Write-Host "Project MySQL already initialized at $DataDir"
        Start-Server
        Initialize-Databases
        return
    }

    $rootPwd = Initialize-Secret 'RESERVATION_MYSQL_ROOT_PASSWORD'
    $appPwd  = Initialize-Secret 'DB_PASSWORD'

    New-Item -ItemType Directory -Force -Path $Base | Out-Null
    $fwd = { param($p) $p.Replace('\', '/') }
    $ini = @"
[mysqld]
basedir="$(& $fwd (Split-Path $MysqlBin -Parent))"
datadir="$(& $fwd $DataDir)"
port=$Port
bind-address=127.0.0.1
mysqlx=OFF
log-error="$(& $fwd $LogFile)"
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
"@
    [IO.File]::WriteAllText($IniFile, $ini, [Text.Encoding]::ASCII)

    # Executed once while the data folder is created, so root never runs without a password.
    $initSql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$(ConvertTo-SqlLiteral $rootPwd)';
CREATE USER '$DbUser'@'localhost' IDENTIFIED BY '$(ConvertTo-SqlLiteral $appPwd)';
"@
    $initFile = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($initFile, $initSql, [Text.Encoding]::ASCII)
        Write-Host "Initializing project MySQL data folder at $DataDir ..."
        & (Join-Path $MysqlBin 'mysqld.exe') "--defaults-file=$IniFile" --initialize-insecure "--init-file=$initFile"
        if ($LASTEXITCODE -ne 0) { throw "mysqld --initialize failed (exit $LASTEXITCODE). See $LogFile" }
    }
    finally {
        Remove-Item $initFile -Force -ErrorAction SilentlyContinue
    }

    Start-Server
    Initialize-Databases
    Write-Host 'Open a NEW terminal / restart your IDE so it sees DB_URL, DB_TEST_URL and DB_PASSWORD.'
}

function Stop-Server {
    if (-not (Test-ServerRunning)) { Write-Host 'Project MySQL is not running.'; return }
    Invoke-RootClient 'mysqladmin.exe' @('shutdown') $null
    for ($i = 0; $i -lt 60; $i++) {
        if (-not (Test-ServerRunning)) { Write-Host 'Project MySQL stopped.'; return }
        Start-Sleep -Milliseconds 500
    }
    throw 'Project MySQL did not stop in 30 seconds.'
}

function Show-Status {
    Write-Host "Data folder : $DataDir"
    Write-Host "Port        : $Port"
    if (Test-ServerRunning) {
        Write-Host 'Running     : yes'
        Invoke-RootClient 'mysql.exe' @('--table') "SELECT VERSION() AS version, @@port AS port, @@datadir AS datadir; SELECT user, host FROM mysql.user WHERE user = '$DbUser'; SHOW DATABASES LIKE '$DbName%';"
    }
    else {
        Write-Host 'Running     : no'
    }
}

switch ($Action) {
    'init'   { Initialize-Server }
    'start'  { Start-Server }
    'stop'   { Stop-Server }
    'status' { Show-Status }
}
