@echo off
setlocal
pushd "%~dp0"
where node >nul 2>&1
if errorlevel 1 (
    if exist "%~dp0.cache\node\node-v22.23.4-win-x64\node.exe" (
        set "PATH=%~dp0.cache\node\node-v22.23.4-win-x64;%PATH%"
    ) else (
        echo Node.js 22.12+ is required to build the React frontend.
        popd
        exit /b 1
    )
)
call "%~dp0mvnw.cmd" clean package %*
set "build_result=%errorlevel%"
popd
exit /b %build_result%
