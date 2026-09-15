param(
    [Parameter(Mandatory = $true)][string]$AppImageRoot,
    [Parameter(Mandatory = $true)][string]$IndependentReasoningJar
)

$ErrorActionPreference = 'Stop'

$appImage = (Resolve-Path $AppImageRoot).Path
$fixture = (Resolve-Path $IndependentReasoningJar).Path
if ($IsWindows) {
    $launcher = Join-Path $appImage 'madre.exe'
} else {
    $launcher = Join-Path $appImage 'bin/madre'
}
if (-not (Test-Path $launcher)) { throw "packaged launcher is missing: $launcher" }

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('madre-reasoning-config-' + [guid]::NewGuid())
$emptyPath = Join-Path $temp 'empty-path'
New-Item -ItemType Directory -Force -Path $emptyPath | Out-Null
$oldPath = $env:PATH
$oldHome = $env:HOME
$oldAppData = $env:APPDATA
$oldLocalAppData = $env:LOCALAPPDATA
$oldXdgConfig = $env:XDG_CONFIG_HOME
$oldXdgData = $env:XDG_DATA_HOME
$oldXdgState = $env:XDG_STATE_HOME

function Invoke-Madre([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        Write-Host $output
        throw "MADRE invocation failed with exit code $LASTEXITCODE"
    }
    Write-Host $output
    return $output
}

function Invoke-MadreFailure([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    $status = $LASTEXITCODE
    Write-Host $output
    if ($status -eq 0) { throw 'MADRE invocation unexpectedly succeeded' }
    return $output
}

try {
    $env:PATH = $emptyPath
    $env:HOME = Join-Path $temp 'home'
    if ($IsWindows) {
        $env:APPDATA = Join-Path $temp 'roaming'
        $env:LOCALAPPDATA = Join-Path $temp 'local'
        $configuration = Join-Path $env:APPDATA 'MADRE/madre.properties'
        $ownerReasoning = Join-Path $env:LOCALAPPDATA 'MADRE/reasoning'
    } else {
        $env:XDG_CONFIG_HOME = Join-Path $temp 'xdg-config'
        $env:XDG_DATA_HOME = Join-Path $temp 'xdg-data'
        $env:XDG_STATE_HOME = Join-Path $temp 'xdg-state'
        $configuration = Join-Path $env:XDG_CONFIG_HOME 'madre/madre.properties'
        $ownerReasoning = Join-Path $env:XDG_DATA_HOME 'madre/reasoning'
    }
    New-Item -ItemType Directory -Force -Path $env:HOME, $ownerReasoning | Out-Null
    Copy-Item $fixture (Join-Path $ownerReasoning 'independent-reasoning.jar') -Force

    $initial = Invoke-Madre @('doctor')
    if ($initial -notmatch 'reasoning\.providers\.count\s+4') {
        throw 'fresh packaged installation did not discover shipped plus independent provider types'
    }
    if ($initial -notmatch 'reasoning\.provider\s+independent-text') {
        throw 'independent provider identity was not discovered from owner reasoning directory'
    }
    if ($initial -notmatch 'reasoning\.instances\.count\s+0' -or
            $initial -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'installing the independent provider silently configured or enabled a mechanism'
    }

    $providers = Invoke-Madre @('reasoning', 'providers')
    if ($providers -notmatch 'reasoning\.provider\s+independent-text\s+Independent deterministic text') {
        throw 'generic provider discovery did not render independent provider-owned metadata'
    }
    if ($providers -notmatch 'field\s+privacy\s+CHOICE\s+required') {
        throw 'generic provider discovery did not render provider-owned field metadata'
    }

    Invoke-Madre @('reasoning', 'configure', 'independent-text', 'preserved',
        '--set', 'capability-id=independent-preserved', '--set', 'privacy=SECRET',
        '--set', 'location=LOCAL') | Out-Null
    Invoke-Madre @('reasoning', 'disable', 'independent-text/preserved') | Out-Null

    Invoke-Madre @('reasoning', 'configure', 'independent-text', 'deterministic',
        '--set', 'capability-id=independent-text', '--set', 'privacy=SECRET',
        '--set', 'location=LOCAL') | Out-Null
    $configuredHash = (Get-FileHash $configuration -Algorithm SHA256).Hash
    $invalid = Invoke-MadreFailure @('reasoning', 'configure', 'independent-text', 'deterministic',
        '--set', 'privacy=NOT_A_PRIVACY')
    if ($invalid -notmatch 'not a valid Privacy') {
        throw 'invalid provider-owned value did not return an actionable provider validation message'
    }
    $afterInvalidHash = (Get-FileHash $configuration -Algorithm SHA256).Hash
    if ($configuredHash -ne $afterInvalidHash) {
        throw 'failed provider validation changed persisted owner configuration'
    }

    $inspect = Invoke-Madre @('reasoning', 'inspect', 'independent-text/deterministic')
    if ($inspect -notmatch 'reasoning\.state\s+enabled' -or
            $inspect -notmatch 'reasoning\.field\s+privacy\s+SECRET') {
        throw 'generic reasoning inspection did not expose the configured independent instance'
    }
    if ($inspect -match 'reasoning\.independent-text\.') {
        throw 'generic reasoning inspection leaked raw internal property names'
    }

    $doctor = Invoke-Madre @('doctor')
    if ($doctor -notmatch 'reasoning\.instance\s+independent-text/deterministic\s+enabled') {
        throw 'doctor did not distinguish the configured independent provider instance'
    }
    if ($doctor -notmatch 'reasoning\.mechanisms\.count\s+1' -or
            $doctor -notmatch 'reasoning\.mechanism\s+independent-text') {
        throw 'restart did not materialize the configured independent mechanism'
    }
    if ($doctor -match 'reasoning\..*(privacy|location|latency|preference|gate-file)') {
        throw 'doctor exposed provider configuration values'
    }

    $restart = Invoke-Madre @('doctor')
    if ($restart -notmatch 'reasoning\.mechanisms\.count\s+1') {
        throw 'configured independent mechanism did not survive another restart'
    }

    Invoke-Madre @('reasoning', 'disable', 'independent-text/deterministic') | Out-Null
    $disabled = Invoke-Madre @('doctor')
    if ($disabled -notmatch 'reasoning\.instance\s+independent-text/deterministic\s+disabled' -or
            $disabled -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'disabled independent instance still materialized after restart'
    }
    if (-not (Test-Path (Join-Path $ownerReasoning 'independent-reasoning.jar'))) {
        throw 'disabling a provider instance removed its installed provider artifact'
    }

    Invoke-Madre @('reasoning', 'enable', 'independent-text/deterministic') | Out-Null
    $reenabled = Invoke-Madre @('doctor')
    if ($reenabled -notmatch 'reasoning\.mechanisms\.count\s+1') {
        throw 'generic enable did not re-materialize the independent instance'
    }

    Invoke-Madre @('reasoning', 'remove', 'independent-text/deterministic') | Out-Null
    $remaining = Invoke-Madre @('reasoning', 'list')
    if ($remaining -notmatch 'reasoning\.instances\.count\s+1' -or
            $remaining -notmatch 'independent-text/preserved\s+disabled' -or
            $remaining -match 'independent-text/deterministic') {
        throw 'removal did not delete exactly the selected independent instance configuration'
    }
    $afterRemove = Invoke-Madre @('doctor')
    if ($afterRemove -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'removing the selected enabled instance did not return materialization to zero'
    }
    if (-not (Test-Path (Join-Path $ownerReasoning 'independent-reasoning.jar'))) {
        throw 'removing provider instance configuration removed its installed provider artifact'
    }
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
