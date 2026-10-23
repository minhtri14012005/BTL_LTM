@echo off
setlocal
pushd "%~dp0"
if not exist "target\multigame-0.0.1-SNAPSHOT.jar" (
    echo Run build.cmd first.
    popd
    exit /b 1
)
if not defined SERVER_PORT set "SERVER_PORT=8080"
if not defined SERVER_ADDRESS set "SERVER_ADDRESS=0.0.0.0"
if not defined ALLOWED_ORIGINS (
    for /f "delims=" %%O in ('powershell.exe -NoProfile -Command "$origins=@(('http://localhost:'+ $env:SERVER_PORT),('http://127.0.0.1:'+ $env:SERVER_PORT)); [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() | Where-Object OperationalStatus -eq Up | ForEach-Object { $_.GetIPProperties().UnicastAddresses } | Where-Object { $_.Address.AddressFamily -eq 'InterNetwork' -and -not [Net.IPAddress]::IsLoopback($_.Address) } | ForEach-Object { $origins+=('http://'+$_.Address.IPAddressToString+':'+$env:SERVER_PORT) }; $origins -join ','"') do set "ALLOWED_ORIGINS=%%O"
)
echo URLs allowed: %ALLOWED_ORIGINS%
java -jar "target\multigame-0.0.1-SNAPSHOT.jar" --spring.profiles.active=mysql %*
set "run_result=%errorlevel%"
popd
exit /b %run_result%
