param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$Database,
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$DatabaseHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$DatabasePort = 3306,
    [string]$Username,
    [string]$DisplayName,
    [string]$ConfigFile
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJar = Join-Path $taskRoot 'target/multigame-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $taskJar)) { throw 'Run mvn package first.' }
if (!$ConfigFile -and !$Username -and !$DisplayName) {
    $defaultConfig = Join-Path $taskRoot 'config/admin.local.psd1'
    if (Test-Path -LiteralPath $defaultConfig) { $ConfigFile = $defaultConfig }
}
$taskSecret = $null
if ($ConfigFile) {
    if ($Username -or $DisplayName) { throw 'Use either ConfigFile or Username/DisplayName, not both.' }
    $adminConfig = Import-PowerShellDataFile -LiteralPath $ConfigFile
    if (@($adminConfig.Keys | Where-Object { $_ -notin @('Username','DisplayName','Password') }).Count) {
        throw 'ADMIN config only supports Username, DisplayName and Password.'
    }
    foreach ($key in @('Username','DisplayName','Password')) {
        if ($adminConfig[$key] -isnot [string] -or [string]::IsNullOrWhiteSpace($adminConfig[$key])) {
            throw 'ADMIN config requires nonempty Username, DisplayName and Password strings.'
        }
    }
    $Username = $adminConfig.Username
    $DisplayName = $adminConfig.DisplayName
    $taskSecret = ConvertTo-SecureString -String $adminConfig.Password -AsPlainText -Force
    $adminConfig.Clear()
}
if (!$Username) { $Username = Read-Host 'ADMIN username' }
if (!$DisplayName) { $DisplayName = Read-Host 'ADMIN display name' }
if (!$taskSecret) { $taskSecret = Read-Host 'ADMIN password (password-manager generated, min 16 characters, max 72 UTF-8 bytes, 3 character classes)' -AsSecureString }
$previousName=$env:MULTIGAME_ADMIN_USERNAME
$previousDisplay=$env:MULTIGAME_ADMIN_DISPLAY_NAME
$previousSecret=$env:MULTIGAME_ADMIN_PASSWORD
$secretPointer=[IntPtr]::Zero
Push-Location -LiteralPath $taskRoot
try {
    $secretPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecret)
    $env:MULTIGAME_ADMIN_USERNAME=$Username
    $env:MULTIGAME_ADMIN_DISPLAY_NAME=$DisplayName
    $env:MULTIGAME_ADMIN_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    $taskUrl="jdbc:mysql://${DatabaseHost}:${DatabasePort}/${Database}?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000"
    & java -jar $taskJar --provision-admin "--spring.datasource.url=$taskUrl" --debug=false --logging.level.root=WARN --logging.level.org.springframework=WARN --logging.level.org.hibernate=WARN
    if ($LASTEXITCODE -ne 0) { throw 'ADMIN provision failed; credentials were not printed.' }
} finally {
    $env:MULTIGAME_ADMIN_USERNAME=$previousName
    $env:MULTIGAME_ADMIN_DISPLAY_NAME=$previousDisplay
    $env:MULTIGAME_ADMIN_PASSWORD=$previousSecret
    if ($secretPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    $taskSecret.Dispose()
    Pop-Location
}
