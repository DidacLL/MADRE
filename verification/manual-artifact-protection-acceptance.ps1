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
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('madre-manual-artifacts-' + [guid]::NewGuid())
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

function Assert-ManualRefusal([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -eq 0) { Write-Host $output; throw 'MADRE invocation unexpectedly succeeded' }
    if ($output -notmatch 'manually placed owner JAR') {
        Write-Host $output
        throw "managed lifecycle did not identify manual artifact for: $($arguments -join ' ')"
    }
}

try {
    $env:HOME = Join-Path $temp 'home'
    New-Item -ItemType Directory -Force -Path $env:HOME | Out-Null
    if ($IsWindows) {
        $env:APPDATA = Join-Path $temp 'roaming'
        $env:LOCALAPPDATA = Join-Path $temp 'local'
        $configuration = Join-Path $env:APPDATA 'MADRE/madre.properties'
        $data = Join-Path $env:LOCALAPPDATA 'MADRE'
    } else {
        $env:XDG_CONFIG_HOME = Join-Path $temp 'config'
        $env:XDG_DATA_HOME = Join-Path $temp 'data'
        $env:XDG_STATE_HOME = Join-Path $temp 'state'
        $configuration = Join-Path $env:XDG_CONFIG_HOME 'madre/madre.properties'
        $data = Join-Path $env:XDG_DATA_HOME 'madre'
    }

    Invoke-Madre @('doctor') | Out-Null
    $moduleDirectory = Join-Path $data 'modules'
    $reasoningDirectory = Join-Path $data 'reasoning'
    $manualModule = Join-Path $moduleDirectory 'manual-independent-module.jar'
    $manualReasoning = Join-Path $reasoningDirectory 'manual-independent-reasoning.jar'
    Copy-Item $module $manualModule
    Copy-Item $reasoning $manualReasoning
    $moduleHash = (Get-FileHash $manualModule -Algorithm SHA256).Hash
    $reasoningHash = (Get-FileHash $manualReasoning -Algorithm SHA256).Hash

    $modules = Invoke-Madre @('modules', 'list')
    if ($modules -notmatch 'module\s+phd\.module.*source=manual') {
        Write-Host $modules
        throw 'manually placed Module was not discoverable and classified as manual'
    }
    $providers = Invoke-Madre @('reasoning', 'providers')
    if ($providers -notmatch 'reasoning\.provider\s+independent-text.*source=manual') {
        Write-Host $providers
        throw 'manually placed reasoning provider was not discoverable and classified as manual'
    }

    Invoke-Madre @('modules', 'configure', 'phd.module', '--set', 'result-prefix=manual-') | Out-Null
    Invoke-Madre @('reasoning', 'configure', 'independent-text', 'preserved',
        '--set', 'capability-id=independent-preserved', '--set', 'privacy=SECRET',
        '--set', 'location=LOCAL') | Out-Null
    $configurationHash = (Get-FileHash $configuration -Algorithm SHA256).Hash

    Assert-ManualRefusal @('modules', 'uninstall', 'phd.module', '--purge-configuration')
    Assert-ManualRefusal @('modules', 'install', $module, '--replace')
    Assert-ManualRefusal @('reasoning', 'uninstall', 'independent-text', '--purge-configuration')
    Assert-ManualRefusal @('reasoning', 'install', $reasoning, '--replace')

    if (-not (Test-Path $manualModule) -or
            (Get-FileHash $manualModule -Algorithm SHA256).Hash -ne $moduleHash) {
        throw 'managed lifecycle changed the manually placed Module JAR'
    }
    if (-not (Test-Path $manualReasoning) -or
            (Get-FileHash $manualReasoning -Algorithm SHA256).Hash -ne $reasoningHash) {
        throw 'managed lifecycle changed the manually placed reasoning JAR'
    }
    if ((Get-FileHash $configuration -Algorithm SHA256).Hash -ne $configurationHash) {
        throw 'refused manual-artifact lifecycle operation changed owner configuration'
    }
} finally {
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
    Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
}
