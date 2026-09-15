param(
    [Parameter(Mandatory = $true)][string]$AppRoot,
    [Parameter(Mandatory = $true)][string]$ModuleJar,
    [Parameter(Mandatory = $true)][string]$ReasoningJar
)

$ErrorActionPreference = 'Stop'
$app = (Resolve-Path $AppRoot).Path
$module = (Resolve-Path $ModuleJar).Path
$reasoning = (Resolve-Path $ReasoningJar).Path
$launcher = if ($IsWindows) { Join-Path $app 'bin/madre.bat' } else { Join-Path $app 'bin/madre' }
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('madre-replacement-failure-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null
$saved = @{
    HOME = $env:HOME
    APPDATA = $env:APPDATA
    LOCALAPPDATA = $env:LOCALAPPDATA
    XDG_CONFIG_HOME = $env:XDG_CONFIG_HOME
    XDG_DATA_HOME = $env:XDG_DATA_HOME
    XDG_STATE_HOME = $env:XDG_STATE_HOME
}

function Invoke-Madre([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { Write-Host $output; throw 'MADRE invocation failed' }
    return $output
}

function Invoke-MadreFailure([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -eq 0) { Write-Host $output; throw 'MADRE invocation unexpectedly succeeded' }
    return $output
}

function New-InvalidJar([string]$path) {
    $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::Create,
        [System.IO.FileAccess]::ReadWrite)
    try {
        $archive = [System.IO.Compression.ZipArchive]::new(
            $stream, [System.IO.Compression.ZipArchiveMode]::Create, $true)
        try {
            $entry = $archive.CreateEntry('marker.txt')
            $writer = [System.IO.StreamWriter]::new($entry.Open())
            try { $writer.Write('not a MADRE provider') } finally { $writer.Dispose() }
        } finally {
            $archive.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

try {
    $env:HOME = Join-Path $temp 'home'
    New-Item -ItemType Directory -Force -Path $env:HOME | Out-Null
    if ($IsWindows) {
        $env:APPDATA = Join-Path $temp 'roaming'
        $env:LOCALAPPDATA = Join-Path $temp 'local'
        $data = Join-Path $env:LOCALAPPDATA 'MADRE'
    } else {
        $env:XDG_CONFIG_HOME = Join-Path $temp 'config'
        $env:XDG_DATA_HOME = Join-Path $temp 'data'
        $env:XDG_STATE_HOME = Join-Path $temp 'state'
        $data = Join-Path $env:XDG_DATA_HOME 'madre'
    }

    Invoke-Madre @('doctor') | Out-Null
    Invoke-Madre @('modules', 'install', $module) | Out-Null
    Invoke-Madre @('reasoning', 'install', $reasoning) | Out-Null

    $moduleFiles = @(Get-ChildItem (Join-Path $data 'modules') -Filter '*.jar')
    $reasoningFiles = @(Get-ChildItem (Join-Path $data 'reasoning') -Filter '*.jar')
    if ($moduleFiles.Count -ne 1 -or $reasoningFiles.Count -ne 1) {
        throw 'expected exactly one managed artifact in each owner root'
    }
    $modulePath = $moduleFiles[0].FullName
    $reasoningPath = $reasoningFiles[0].FullName
    $moduleHash = (Get-FileHash $modulePath -Algorithm SHA256).Hash
    $reasoningHash = (Get-FileHash $reasoningPath -Algorithm SHA256).Hash

    $invalidModule = Join-Path $temp 'invalid-module.jar'
    $invalidReasoning = Join-Path $temp 'invalid-reasoning.jar'
    New-InvalidJar $invalidModule
    New-InvalidJar $invalidReasoning

    $moduleFailure = Invoke-MadreFailure @('modules', 'install', $invalidModule, '--replace')
    if ($moduleFailure -notmatch 'exposes no ModuleProvider') {
        Write-Host $moduleFailure
        throw 'invalid Module replacement did not fail candidate validation'
    }
    if (-not (Test-Path $modulePath) -or
            (Get-FileHash $modulePath -Algorithm SHA256).Hash -ne $moduleHash) {
        throw 'failed Module replacement changed the previously installed artifact'
    }

    $reasoningFailure = Invoke-MadreFailure @(
        'reasoning', 'install', $invalidReasoning, '--replace')
    if ($reasoningFailure -notmatch 'exposes no ReasoningMechanismProvider') {
        Write-Host $reasoningFailure
        throw 'invalid reasoning replacement did not fail candidate validation'
    }
    if (-not (Test-Path $reasoningPath) -or
            (Get-FileHash $reasoningPath -Algorithm SHA256).Hash -ne $reasoningHash) {
        throw 'failed reasoning replacement changed the previously installed artifact'
    }
} finally {
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
    Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
}
