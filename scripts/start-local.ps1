param(
    [ValidateSet('mysql', 'web-only')]
    [string]$Profile = 'mysql',
    [switch]$PromptForPassword
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousPassword = $env:DB_PASSWORD
try {
    if ($Profile -eq 'mysql' -and $PromptForPassword) {
        $securePassword = Read-Host 'MySQL password (not saved to disk)' -AsSecureString
        $passwordHandle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        try {
            $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordHandle)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordHandle)
            $securePassword.Dispose()
        }
    }
    Push-Location -LiteralPath $projectRoot
    try {
        # Native stderr may contain non-fatal JVM warnings in Windows PowerShell.
        $previousErrorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\mvnw.cmd "spring-boot:run" "-Dspring-boot.run.profiles=$Profile"
            $serverExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorPreference
        }
        if ($serverExitCode -ne 0) { throw "Server exited with code $serverExitCode" }
    } finally {
        Pop-Location
    }
} finally {
    $env:DB_PASSWORD = $previousPassword
}
