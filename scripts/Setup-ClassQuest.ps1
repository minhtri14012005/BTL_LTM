[CmdletBinding()]
param(
    [string] $DatabaseConfig,
    [string] $AdminConfig,
    [switch] $SkipBuild,
    [switch] $AdminOnly
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DatabaseConfig)) {
    $DatabaseConfig = Join-Path $projectDirectory 'config/application-local.properties'
}
if ([string]::IsNullOrWhiteSpace($AdminConfig)) {
    $AdminConfig = Join-Path $projectDirectory 'config/admin.local.psd1'
}

function Read-DatabaseConfig {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing database config: $Path" }
    $values = @{ DB_HOST = '127.0.0.1'; DB_PORT = '3306'; DB_NAME = 'quizz'; DB_USER = 'root'; DB_PASSWORD = '' }
    foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path).Path, [Text.Encoding]::UTF8)) {
        if ($line.TrimStart().StartsWith('#') -or $line.TrimStart().StartsWith('!')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -lt 0) { continue }
        $key = $line.Substring(0, $separator).Trim()
        if ($values.ContainsKey($key)) { $values[$key] = $line.Substring($separator + 1).Trim() }
    }
    if ($values.DB_NAME -notmatch '^[A-Za-z0-9_]{1,64}$') { throw 'DB_NAME must contain 1-64 letters, digits or underscores.' }
    if ($values.DB_PORT -notmatch '^\d{1,5}$' -or [int]$values.DB_PORT -lt 1 -or [int]$values.DB_PORT -gt 65535) { throw 'DB_PORT must be between 1 and 65535.' }
    if ([string]::IsNullOrWhiteSpace($values.DB_HOST) -or [string]::IsNullOrWhiteSpace($values.DB_USER)) { throw 'DB_HOST and DB_USER are required.' }
    return $values
}

function Find-MySql {
    $command = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidates = @(Get-ChildItem -Path "$env:ProgramFiles\MySQL\*\bin\mysql.exe" -File -ErrorAction SilentlyContinue)
    if ($candidates.Count) { return $candidates[0].FullName }
    throw 'Cannot find mysql.exe. Install MySQL Server 8.x or add its bin folder to PATH.'
}

$savedEnvironment = @{}
$environmentNames = @('MYSQL_PWD', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'MULTIGAME_ADMIN_USERNAME', 'MULTIGAME_ADMIN_DISPLAY_NAME', 'MULTIGAME_ADMIN_PASSWORD')
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$setupExitCode = 0
Push-Location -LiteralPath $projectDirectory
try {
    if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) { throw 'Install JDK 21 and add Java to PATH, then open a new terminal.' }
    $database = Read-DatabaseConfig $DatabaseConfig
    if (-not (Test-Path -LiteralPath $AdminConfig -PathType Leaf)) { throw "Missing ADMIN config: $AdminConfig" }
    $administrator = Import-PowerShellDataFile -LiteralPath $AdminConfig
    foreach ($key in @('Username', 'DisplayName', 'Password')) {
        if (-not ($administrator[$key] -is [string]) -or [string]::IsNullOrWhiteSpace($administrator[$key])) { throw "ADMIN config needs a non-empty $key." }
    }
    if (-not $AdminOnly) {
        $mysql = Find-MySql
        $env:MYSQL_PWD = $database.DB_PASSWORD
        # The identifier is validated above; an existing database is never cleared.
        $createDatabase = 'CREATE DATABASE IF NOT EXISTS `' + $database.DB_NAME + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'
        Write-Host "Preparing database $($database.DB_NAME) on $($database.DB_HOST):$($database.DB_PORT)..."
        & $mysql '--no-defaults' "--host=$($database.DB_HOST)" "--port=$($database.DB_PORT)" "--user=$($database.DB_USER)" '--connect-timeout=5' '--default-character-set=utf8mb4' '--execute' $createDatabase
        if ($LASTEXITCODE -ne 0) { throw "MySQL connection/database creation failed. Start MySQL and check $DatabaseConfig." }
        if (-not $SkipBuild) {
            Write-Host 'Building ClassQuest (frontend, Java tests and JAR)...'
            & (Join-Path $projectDirectory 'build.cmd')
            if ($LASTEXITCODE -ne 0) { throw 'Build failed. Check JDK 21, Node.js 22.12+ and your Internet connection.' }
        }
    }
    $jar = Join-Path $projectDirectory 'target/multigame-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'JAR not found. Run setup.cmd or build.cmd first.' }
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://$($database.DB_HOST):$($database.DB_PORT)/$($database.DB_NAME)?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"
    $env:SPRING_DATASOURCE_USERNAME = $database.DB_USER
    $env:SPRING_DATASOURCE_PASSWORD = $database.DB_PASSWORD
    $env:MULTIGAME_ADMIN_USERNAME = $administrator.Username
    $env:MULTIGAME_ADMIN_DISPLAY_NAME = $administrator.DisplayName
    $env:MULTIGAME_ADMIN_PASSWORD = $administrator.Password
    Write-Host 'Applying Flyway migrations and provisioning ADMIN (without starting the web server)...'
    & java.exe '-jar' $jar '--provision-admin'
    if ($LASTEXITCODE -ne 0) { throw 'ADMIN provisioning failed. See the error above; existing accounts are not overwritten.' }
    Write-Host "Completed. ADMIN username: $($administrator.Username). Credentials are in $AdminConfig."
    Write-Host 'No web server was started. Run .\run.cmd when you want to start ClassQuest.'
} catch {
    [Console]::Error.WriteLine("ClassQuest setup failed: $($_.Exception.Message)")
    $setupExitCode = 1
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    Pop-Location
}
exit $setupExitCode
