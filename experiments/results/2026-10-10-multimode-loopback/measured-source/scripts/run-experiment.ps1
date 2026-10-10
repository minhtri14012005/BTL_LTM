param(
    [string]$OutputDirectory='experiments/results/local-run',
    [ValidateSet('baseline','proposed','both')][string]$Mode='both',
    [int[]]$Loads=@(3,5,10,20), [int]$Rounds=3, [int]$ReliabilityGames=10,
    [int]$Port=8080, [switch]$SkipBuild, [switch]$Multimode
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $outputPath=[IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
    if(!$outputPath.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Evidence output must stay inside this project.'}
    if(Test-Path -LiteralPath (Join-Path $outputPath 'environment.json')){throw 'Run directory already has evidence. Choose a new OutputDirectory; do not overwrite environment/config.'}
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    if(!$SkipBuild){
        $env:MAVEN_USER_HOME=Join-Path $projectRoot '.cache/maven-home'
        & .\mvnw.cmd -B --no-transfer-progress test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/experiment-classpath.txt' > (Join-Path $outputPath 'compile.log')
        if($LASTEXITCODE -ne 0){throw 'Experiment compile failed; see compile.log'}
    }
    $classPath=(Join-Path $projectRoot 'target/test-classes')+';'+(Join-Path $projectRoot 'target/classes')+';'+(Get-Content -Raw target/experiment-classpath.txt).Trim()
    $java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{(Get-Command java).Source}
    if(!(Test-Path -LiteralPath $java)){$java=(Get-Command java).Source}
    $environment=[ordered]@{ date=(Get-Date -Format o); java=(Get-Item -LiteralPath $java).VersionInfo.ProductVersion; python=(& python --version); origin="http://127.0.0.1:$Port"; placement='same-machine loopback'; db='quizz_task15_experiment'; loads=$Loads; rounds=$Rounds; reliabilityGames=$ReliabilityGames; seed=15062026; cpu=(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors); memoryBytes=(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory; os=(Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version); serverWorkers=4; hikariMaxPool=5; decisionDurationMs=7000; rulesVersion=$(if($Multimode){2}else{1}); stageModes=$(if($Multimode){@('QUIZ','RIDDLE')}else{@('QUIZ')}); questionDurationMs=10000; baseline='test-classpath receipt erasure + reconnect Room subscription without full Game snapshot'; defaultDemoUnchanged=$true }
    $environment | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 (Join-Path $outputPath 'environment.json')
    $modes=if($Mode -eq 'both'){@('baseline','proposed')}else{@($Mode)}
    foreach($experimentMode in $modes){
        if(Test-Path -LiteralPath (Join-Path $outputPath "$experimentMode-client.csv")){throw 'Output already contains a run. Choose a new OutputDirectory; do not overwrite evidence.'}
        $args=@("-Dexperiment.mode=$experimentMode",('-Dexperiment.metrics="'+(Join-Path $outputPath "$experimentMode-server.csv")+'"'),'-cp',('"'+$classPath+'"'),'vn.edu.quiz.realtime.session.ExperimentServer','--spring.profiles.active=mysql,experiment','--DB_NAME=quizz_task15_experiment',"--server.port=$Port",'--server.address=127.0.0.1',"--ALLOWED_ORIGINS=http://127.0.0.1:$Port",'--QUIZ_IMAGE_DIRECTORY=./target/experiment-images','--debug=false','--logging.level.root=INFO')
        $server=Start-Process -FilePath $java -ArgumentList $args -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $outputPath "$experimentMode-server.log") -RedirectStandardError (Join-Path $outputPath "$experimentMode-server-error.log") -PassThru
        try {
            $ready=$false
            for($attempt=0;$attempt -lt 180;$attempt++){
                if($server.HasExited){throw 'Experiment Server exited; see server logs.'}
                try{$r=Invoke-RestMethod "http://127.0.0.1:$Port/api/system/ready";if($r.database.status -eq 'UP'){$ready=$true;break}}catch{}
                Start-Sleep -Milliseconds 500
            }
            if(!$ready){throw 'Server/MySQL readiness timeout'}
            $clientArgs=@('scripts/experiment-client.py','--origin',"http://127.0.0.1:$Port",'--mode',$experimentMode,'--output',$outputPath,'--rounds',"$Rounds",'--reliability-games',"$ReliabilityGames",'--loads')+@($Loads | ForEach-Object {"$_"})
            if($Multimode){$clientArgs+='--multimode'}
            & python @clientArgs > (Join-Path $outputPath "$experimentMode-client.log")
            if($LASTEXITCODE -ne 0){throw 'Experiment Client failed; partial CSV/log retained.'}
        } finally {
            $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($server.Id)" -ErrorAction SilentlyContinue
            if($owned -and $owned.Name -eq 'java.exe' -and $owned.CommandLine.Contains('vn.edu.quiz.realtime.session.ExperimentServer') -and $owned.CommandLine.Contains("-Dexperiment.mode=$experimentMode")){Stop-Process -Id $server.Id -Force}
        }
    }
    & python scripts/summarize-experiment.py $outputPath
    if($LASTEXITCODE -ne 0){throw 'Experiment summary failed'}
} finally {Pop-Location}
