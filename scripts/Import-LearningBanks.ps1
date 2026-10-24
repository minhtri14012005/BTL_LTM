[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [uri] $BaseUrl,
    [System.Management.Automation.PSCredential] $Credential,
    [string] $DataFile,
    [switch] $ValidateOnly
)
$ErrorActionPreference = 'Stop'
if ($BaseUrl.Scheme -notin @('http', 'https') -or $BaseUrl.AbsolutePath -ne '/') {
    throw 'BaseUrl must be the HTTP(S) origin of ClassQuest, without an API path.'
}
if ([string]::IsNullOrWhiteSpace($DataFile)) {
    # Resolve automatic script variables after parameter binding (Windows PowerShell 5).
    $importScriptDirectory = $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($importScriptDirectory) -and $PSCommandPath) {
        $importScriptDirectory = Split-Path -Parent $PSCommandPath
    }
    if ([string]::IsNullOrWhiteSpace($importScriptDirectory)) {
        throw 'Cannot locate the importer directory. Provide -DataFile with an absolute path.'
    }
    $DataFile = Join-Path $importScriptDirectory '../data/learning-banks/question-banks.json'
}
$endpoint = $BaseUrl.AbsoluteUri.TrimEnd('/')
$dataset = Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $dataset.packs -or $dataset.packs.Count -ne 15) {
    throw 'Expected the dataset containing 15 learning banks.'
}
if ($ValidateOnly) {
    Write-Output "Validated $($dataset.packs.Count) learning banks: $([IO.Path]::GetFullPath($DataFile))"
    return
}
if (-not $Credential) {
    $Credential = Get-Credential -Message 'Sign in with a ClassQuest teacher account'
}
if (-not $Credential) { throw 'Teacher credentials are required.' }
$learningSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$headers = @{}
function Invoke-LearningApi {
    param([string] $Path, [string] $Method = 'Get', [object] $Payload)
    $request = @{ Uri = "$endpoint$Path"; Method = $Method; WebSession = $learningSession; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Payload) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [Text.Encoding]::UTF8.GetBytes(($Payload | ConvertTo-Json -Depth 20))
    }
    $response = Invoke-WebRequest @request
    # Windows PowerShell 5 may guess the wrong encoding for application/json.
    $response.RawContentStream.Position = 0
    $reader = [IO.StreamReader]::new($response.RawContentStream, [Text.Encoding]::UTF8)
    try { $json = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($json) { return ($json | ConvertFrom-Json) }
}
$csrf = Invoke-LearningApi '/api/auth/csrf'
$headers = @{ ($csrf.headerName) = $csrf.token }
$loginBody = @{ username = $Credential.UserName; password = $Credential.GetNetworkCredential().Password }
try { $identity = Invoke-LearningApi '/api/auth/login' 'Post' $loginBody }
finally { $loginBody = $null }
if ($identity.role -ne 'GIANG_VIEN') { throw 'Only a teacher account can import learning banks.' }
$csrf = Invoke-LearningApi '/api/auth/csrf'
$headers = @{ ($csrf.headerName) = $csrf.token }
$created = 0
$skipped = 0
foreach ($pack in $dataset.packs) {
    if ($pack.mode -notin @('QUIZ', 'FILL_BLANK', 'ASSIGNMENT')) { throw 'Unsupported bank mode.' }
    $search = [uri]::EscapeDataString($pack.title)
    $page = 0
    $exists = $false
    do {
        $result = Invoke-LearningApi "/api/quizzes?scope=MINE&mode=$($pack.mode)&q=$search&page=$page&size=100"
        $exists = @($result.items | Where-Object { $_.title -ceq $pack.title -and $_.mode -eq $pack.mode }).Count -gt 0
        $page++
    } while (-not $exists -and $page * 100 -lt $result.totalElements)
    if ($exists) {
        $skipped++
        Write-Host "Skipped existing owned bank: $($pack.title)"
        continue
    }
    $bank = Invoke-LearningApi "/api/quizzes" "Post" $pack
    $created++
    Write-Host "Created bank #$($bank.id): $($pack.title)"
}
Write-Host "Completed. Created: $created; skipped existing owned banks: $skipped."
