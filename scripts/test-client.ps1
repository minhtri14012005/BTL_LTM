param([string]$Origin='http://127.0.0.1:8080', [int]$Port=9223, [switch]$UnitOnly, [switch]$Gameplay, [string]$ReportName='task12-client-tests.json')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$chromePath=Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'
if (!(Test-Path -LiteralPath $chromePath)) {throw 'Chrome not found; pass an owned headless Chrome to test-client.py instead.'}
$profile=Join-Path $projectRoot ('target\client-chrome-'+[Guid]::NewGuid().ToString('N'))
$chrome=Start-Process -FilePath $chromePath -ArgumentList @('--headless=new',"--remote-debugging-port=$Port",'--remote-debugging-address=127.0.0.1',('--user-data-dir="'+$profile+'"'),'--no-first-run','--no-default-browser-check','--window-size=1440,1000','about:blank') -WindowStyle Hidden -PassThru
try {
    $ready=$false
    for($attempt=0;$attempt -lt 50;$attempt++) {try {$null=Invoke-RestMethod "http://127.0.0.1:$Port/json/version"; $ready=$true;break}catch {Start-Sleep -Milliseconds 100}}
    if(!$ready){throw 'Owned Chrome CDP did not become ready.'}
    $testFile=if($Gameplay){'test-game-client.py'}else{'test-client.py'}
    $testArgs=@((Join-Path $PSScriptRoot $testFile),'--origin',$Origin,'--port',"$Port",'--close-browser')
    if(!$UnitOnly){$testArgs+='--smoke'}
    if($Gameplay){$testArgs+=@('--report-name',$ReportName)}
    & python @testArgs
    if($LASTEXITCODE -ne 0){throw "Client test failed: $LASTEXITCODE"}
} finally {
    # Only kill the process created here, verified by its unique profile argument.
    $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($chrome.Id)" -ErrorAction SilentlyContinue
    if($owned -and $owned.Name -eq 'chrome.exe' -and $owned.CommandLine -and $owned.CommandLine.Contains($profile)){Stop-Process -Id $chrome.Id -Force -ErrorAction SilentlyContinue}
}
