param(
    [Parameter(Mandatory = $true)][string]$AppImageRoot,
    [Parameter(Mandatory = $true)][string]$NativePackageDirectory
)

$ErrorActionPreference = 'Stop'

$appImage = (Resolve-Path $AppImageRoot).Path
$nativePackages = (Resolve-Path $NativePackageDirectory).Path
if ($IsWindows) {
    $launcher = Join-Path $appImage 'madre.exe'
    $runtime = Join-Path $appImage 'runtime'
    $appDirectory = Join-Path $appImage 'app'
    $native = @(Get-ChildItem $nativePackages -Filter '*.msi')
} else {
    $launcher = Join-Path $appImage 'bin/madre'
    $runtime = Join-Path $appImage 'lib/runtime'
    $appDirectory = Join-Path $appImage 'lib/app'
    $native = @(Get-ChildItem $nativePackages -Filter '*.deb')
}
if (-not (Test-Path $launcher)) { throw "packaged launcher is missing: $launcher" }
if (-not (Test-Path $runtime)) { throw "bundled runtime is missing: $runtime" }
if ($native.Count -ne 1) { throw "expected exactly one native package, found $($native.Count)" }

$shippedModules = Join-Path $appDirectory 'modules'
$shippedReasoning = Join-Path $appDirectory 'reasoning'
if (-not (Test-Path $shippedModules)) { throw 'packaged shipped Module directory is missing' }
if (-not (Test-Path $shippedReasoning)) { throw 'packaged shipped reasoning directory is missing' }
if (@(Get-ChildItem $shippedModules -Filter 'madre-module-owner-interaction*.jar').Count -lt 1) {
    throw 'packaged owner-interaction Module is missing'
}
if (@(Get-ChildItem $shippedReasoning -Filter 'madre-adapter-llamacpp*.jar').Count -lt 1) {
    throw 'packaged llama.cpp reasoning adapter is missing'
}
if (@(Get-ChildItem $shippedReasoning -Filter 'madre-adapter-openai-compatible*.jar').Count -lt 1) {
    throw 'packaged OpenAI-compatible reasoning adapter is missing'
}

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('madre-native-acceptance-' + [guid]::NewGuid())
$emptyPath = Join-Path $temp 'empty-path'
$work = Join-Path $temp 'work'
New-Item -ItemType Directory -Force -Path $emptyPath, $work | Out-Null

$oldPath = $env:PATH
$oldHome = $env:HOME
$oldAppData = $env:APPDATA
$oldLocalAppData = $env:LOCALAPPDATA
$oldXdgConfig = $env:XDG_CONFIG_HOME
$oldXdgData = $env:XDG_DATA_HOME
$oldXdgState = $env:XDG_STATE_HOME

try {
    $env:PATH = $emptyPath
    $env:HOME = Join-Path $temp 'home'
    if ($IsWindows) {
        $env:APPDATA = Join-Path $temp 'roaming'
        $env:LOCALAPPDATA = Join-Path $temp 'local'
        $configuration = Join-Path $env:APPDATA 'MADRE/madre.properties'
        $dataDirectory = Join-Path $env:LOCALAPPDATA 'MADRE'
        $stateDirectory = Join-Path $dataDirectory 'state'
    } else {
        $env:XDG_CONFIG_HOME = Join-Path $temp 'xdg-config'
        $env:XDG_DATA_HOME = Join-Path $temp 'xdg-data'
        $env:XDG_STATE_HOME = Join-Path $temp 'xdg-state'
        $configuration = Join-Path $env:XDG_CONFIG_HOME 'madre/madre.properties'
        $dataDirectory = Join-Path $env:XDG_DATA_HOME 'madre'
        $stateDirectory = Join-Path $env:XDG_STATE_HOME 'madre'
    }

    if (Get-Command java -ErrorAction SilentlyContinue) {
        throw 'java unexpectedly remains available through PATH during packaged-runtime proof'
    }

    Push-Location $work
    try {
        $first = (('/exit' + [Environment]::NewLine) | & $launcher 2>&1 | Out-String)
        $firstStatus = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    Write-Host $first
    if ($firstStatus -ne 0) { throw "fresh zero-argument packaged launch failed with $firstStatus" }
    if ($first -notmatch 'MADRE ready') { throw 'fresh zero-argument packaged launch did not reach the console' }
    if (-not (Test-Path $configuration)) { throw 'first run did not persist owner configuration' }
    if (-not (Test-Path (Join-Path $stateDirectory 'kernel-work.sqlite'))) {
        throw 'first run did not create the Kernel durable database in the product state location'
    }
    if (-not (Test-Path (Join-Path $stateDirectory 'module-state'))) {
        throw 'first run did not create the Module state directory in the product state location'
    }
    if (-not (Test-Path (Join-Path $dataDirectory 'modules'))) {
        throw 'first run did not create the owner-writable Module artifact directory'
    }
    if (-not (Test-Path (Join-Path $dataDirectory 'reasoning'))) {
        throw 'first run did not create the owner-writable reasoning artifact directory'
    }
    if ($configuration.StartsWith($appImage, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'owner configuration was written inside the read-only application image'
    }
    if ($configuration.StartsWith($work, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'owner configuration depends on the process current working directory'
    }

    $configurationBefore = (Get-FileHash $configuration -Algorithm SHA256).Hash
    $doctor = (& $launcher doctor 2>&1 | Out-String)
    $doctorStatus = $LASTEXITCODE
    Write-Host $doctor
    if ($doctorStatus -ne 0) { throw "madre doctor failed with $doctorStatus" }
    if ($doctor -notmatch 'configuration.status\s+loaded \(existing\)') {
        throw 'doctor did not report reusing the persisted configuration'
    }
    if ($doctor -notmatch 'module.discovery\s+initialized') {
        throw 'doctor did not report Module discovery initialization'
    }
    if ($doctor -notmatch 'module\s+io\.github\.didacll\.madre\.owner-interaction') {
        throw 'doctor did not discover the shipped owner-interaction Module'
    }
    if ($doctor -notmatch 'core\s+resolved io\.github\.didacll\.madre\.owner-interaction') {
        throw 'doctor did not report the configured/resolved CORE role'
    }
    if ($doctor -notmatch 'reasoning.discovery\s+initialized') {
        throw 'doctor did not report reasoning discovery initialization'
    }
    if ($doctor -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'fresh install silently materialized a reasoning mechanism'
    }
    if ($doctor -notmatch [regex]::Escape((Resolve-Path $runtime).Path)) {
        throw 'doctor does not show the packaged Java runtime as java.home'
    }
    if ($doctor -match 'reasoning\..*(endpoint|model|credential|token|secret)') {
        throw 'doctor exposed provider configuration details'
    }

    $doctorAgain = (& $launcher doctor 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'second doctor launch failed' }
    $configurationAfter = (Get-FileHash $configuration -Algorithm SHA256).Hash
    if ($configurationAfter -ne $configurationBefore) {
        throw 'second launch rewrote established owner configuration'
    }
    if ($doctorAgain -notmatch [regex]::Escape($configuration)) {
        throw 'second launch did not resolve the same owner configuration location'
    }

    Write-Host "Native package: $($native[0].FullName)"
    Write-Host "Persistent configuration: $configuration"
    Write-Host "Persistent data: $dataDirectory"
    Write-Host "Persistent state: $stateDirectory"
} finally {
    $env:PATH = $oldPath
    $env:HOME = $oldHome
    $env:APPDATA = $oldAppData
    $env:LOCALAPPDATA = $oldLocalAppData
    $env:XDG_CONFIG_HOME = $oldXdgConfig
    $env:XDG_DATA_HOME = $oldXdgData
    $env:XDG_STATE_HOME = $oldXdgState
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}
