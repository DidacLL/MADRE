param()

$ErrorActionPreference = 'Stop'

function Slash([string]$value) { return $value.Replace('\', '/') }

$root = (Get-Location).Path
$dist = Join-Path $root 'madre-app/build/install/madre'
$moduleDir = Join-Path $dist 'modules'
$reasoningDir = Join-Path $dist 'reasoning'
$independentModule = Join-Path $root 'verification/sdk-consumer/build/libs/independent-module.jar'
$independentReasoning = Join-Path $root 'verification/reasoning-consumer/build/libs/independent-reasoning.jar'

if (-not (Test-Path $dist)) { throw 'built MADRE distribution is missing' }
if (-not (Test-Path $independentModule)) { throw 'independent Module fixture is missing' }
if (-not (Test-Path $independentReasoning)) { throw 'independent reasoning fixture is missing' }

Copy-Item $independentModule (Join-Path $moduleDir 'independent-module.jar') -Force
Copy-Item $independentReasoning (Join-Path $reasoningDir 'independent-reasoning.jar') -Force

$ownerJar = @(Get-ChildItem $moduleDir -Filter 'madre-module-owner-interaction*.jar')
if ($ownerJar.Count -lt 1) { throw 'shipped owner-interaction Module is missing from modules/' }
if (-not (Test-Path (Join-Path $reasoningDir 'independent-reasoning.jar'))) {
    throw 'independent reasoning adapter is missing from reasoning/'
}

if ($IsWindows) {
    $launcher = Join-Path $dist 'bin/madre.bat'
} else {
    $launcher = Join-Path $dist 'bin/madre'
}

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('madre-owner-acceptance-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null

function Write-Config(
    [string]$path,
    [string]$database,
    [string]$stateDirectory,
    [string]$gateFile,
    [string]$completionFile,
    [bool]$core
) {
    $lines = @(
        "kernel.database=$(Slash $database)",
        "modules.directory=$(Slash $moduleDir)",
        "modules.state-directory=$(Slash $stateDirectory)",
        "reasoning.directory=$(Slash $reasoningDir)",
        'reasoning.independent-text.instances=deterministic',
        'reasoning.independent-text.deterministic.enabled=true',
        'reasoning.independent-text.deterministic.id=independent-text',
        'reasoning.independent-text.deterministic.privacy=SECRET',
        'reasoning.independent-text.deterministic.location=LOCAL',
        'reasoning.independent-text.deterministic.expected-latency-ms=1',
        'reasoning.independent-text.deterministic.preference=1000'
    )
    if ($gateFile) {
        $lines += "reasoning.independent-text.deterministic.background-gate-file=$(Slash $gateFile)"
    }
    if ($completionFile) {
        $lines += "reasoning.independent-text.deterministic.background-completion-file=$(Slash $completionFile)"
    }
    if ($core) {
        $lines += 'roles.core=io.github.didacll.madre.owner-interaction'
    }
    $lines | Set-Content -Path $path -Encoding utf8
}

function Invoke-Madre([string]$config, [string[]]$arguments) {
    $output = (& $launcher $config @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        Write-Host $output
        throw "MADRE invocation failed with exit code $LASTEXITCODE"
    }
    Write-Host $output
    return $output
}

try {
    $standardState = Join-Path $temp 'standard-state'
    $standardDb = Join-Path $temp 'standard.sqlite'
    $standardConfig = Join-Path $temp 'standard.properties'
    Write-Config $standardConfig $standardDb $standardState '' '' $false

    $owner = Invoke-Madre $standardConfig @(
        '--invoke-owner', 'io.github.didacll.madre.owner-interaction', 'standard-prompt',
        'owner-prompt', 'S5', 'sensitive-owner-material')
    if ($owner -notmatch 'S5\s+independent:sensitive-owner-material') {
        throw 'owner-local standard-prompt did not return useful S5 Module Material'
    }

    $public = Invoke-Madre $standardConfig @(
        '--invoke-public', 'io.github.didacll.madre.owner-interaction', 'standard-prompt',
        'owner-prompt', 'S5', 'sensitive-owner-material')
    if ($public -notmatch 'owner-interaction result withheld at public boundary') {
        throw 'PUBLIC standard-prompt did not apply semantic minimization'
    }
    if ($public -match 'independent:sensitive-owner-material') {
        throw 'sensitive owner reasoning leaked through PUBLIC invocation'
    }

    $coreState = Join-Path $temp 'core-state'
    $coreDb = Join-Path $temp 'core.sqlite'
    $coreConfig = Join-Path $temp 'core.properties'
    Write-Config $coreConfig $coreDb $coreState '' '' $true
    $modules = Invoke-Madre $coreConfig @('--list-modules')
    if ($modules -notmatch 'io\.github\.didacll\.madre\.owner-interaction\s+1\.1\.0\s+\[CORE\]') {
        throw 'owner-interaction Module did not resolve as optional CORE role'
    }
    $coreOwner = Invoke-Madre $coreConfig @(
        '--invoke-owner', 'io.github.didacll.madre.owner-interaction', 'standard-prompt',
        'owner-prompt', 'S5', 'sensitive-owner-material')
    if ($coreOwner -notmatch 'S5\s+independent:sensitive-owner-material') {
        throw 'CORE assignment changed owner-local invocation behavior'
    }

    $restartState = Join-Path $temp 'restart-state'
    $restartDb = Join-Path $temp 'restart.sqlite'
    $restartConfig = Join-Path $temp 'restart.properties'
    $gate = Join-Path $temp 'background.gate'
    $completion = Join-Path $temp 'background.complete'
    Write-Config $restartConfig $restartDb $restartState $gate $completion $false

    $foreground = Invoke-Madre $restartConfig @(
        '--invoke-owner', 'io.github.didacll.madre.owner-interaction', 'fast-lane',
        'owner-prompt', 'S4', 'restart-sensitive')
    if ($foreground -notmatch 'S4\s+independent:restart-sensitive') {
        throw 'fast-lane foreground result was not returned through owner-local invocation'
    }
    if (Test-Path $completion) {
        throw 'background reasoning completed before the deterministic restart gate opened'
    }
    $pendingState = Join-Path $restartState 'owner-interaction-background.state'
    if (-not (Test-Path $pendingState) -or (Get-Item $pendingState).Length -eq 0) {
        throw 'Module-owned pending background state was not persisted before restart'
    }

    Set-Content -Path $gate -Value 'open' -Encoding utf8
    $barrier = Invoke-Madre $restartConfig @(
        '--invoke-owner', 'io.github.didacll.madre.owner-interaction', 'standard-prompt',
        'owner-prompt', 'S1', 'await-background-completion')
    if ($barrier -notmatch 'S1\s+independent:await-background-completion') {
        throw 'restart barrier did not complete through the independent reasoning adapter'
    }
    if (-not (Test-Path $completion)) {
        throw 'durable reasoning did not resume and complete after restart'
    }

    $collected = Invoke-Madre $restartConfig @(
        '--invoke-owner', 'io.github.didacll.madre.owner-interaction', 'collect-background',
        'background-collection-request', 'S1', 'collect')
    if ($collected -notmatch 'S4\s+.*independent:background-useful:') {
        throw 'Module did not interpret the completed durable reasoning result after restart'
    }
    if ((Get-Item $pendingState).Length -ne 0) {
        throw 'Module-owned pending state was not removed after collection'
    }

    $empty = Invoke-Madre $restartConfig @(
        '--invoke-owner', 'io.github.didacll.madre.owner-interaction', 'collect-background',
        'background-collection-request', 'S1', 'collect')
    if ($empty -notmatch 'S1\s+no completed background updates') {
        throw 'acknowledged durable reasoning remained visible after cleanup'
    }
} finally {
    Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
}
