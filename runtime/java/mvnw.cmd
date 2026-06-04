@echo off
setlocal EnableDelayedExpansion
set MAVEN_VERSION=3.9.9
set BASE_DIR=%~dp0
set WRAPPER_DIR=%BASE_DIR%.mvn\wrapper
set MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd

if not exist "%MAVEN_CMD%" (
  set PWSH=
  for %%P in (pwsh.exe powershell.exe) do (
    if not defined PWSH (
      for /f "delims=" %%I in ('where %%P 2^>nul') do (
        if not defined PWSH set PWSH=%%I
      )
    )
  )
  if not defined PWSH (
    echo PowerShell 7 or Windows PowerShell is required to download Maven %MAVEN_VERSION%. 1>&2
    exit /b 1
  )
  "!PWSH!" -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $wrapper='%WRAPPER_DIR%'; $zip=Join-Path $wrapper 'apache-maven-%MAVEN_VERSION%-bin.zip'; New-Item -ItemType Directory -Force -Path $wrapper | Out-Null; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath $wrapper -Force"
)

rem Prefer the Java 21 runtime on PATH over an older machine-level JAVA_HOME.
set JAVA_HOME=
call "%MAVEN_CMD%" %*
endlocal
