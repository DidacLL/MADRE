param(
    [Parameter(Mandatory = $true)][string]$AppImageRoot,
    [Parameter(Mandatory = $true)][string]$IndependentReasoningJar
)

$ErrorActionPreference = 'Stop'

$appImage = (Resolve-Path $AppImageRoot).Path
$fixture = (Resolve-Path $IndependentReasoningJar).Path
if ($IsWindows) {
    $launcher = Join-Path $appImage 'madre.exe'
    $shippedReasoning = Join-Path $appImage 'app/reasoning'
} else {
    $launcher = Join-Path $appImage 'bin/madre'
    $shippedReasoning = Join-Path $appImage 'lib/app/reasoning'
}
if (-not (Test-Path $launcher)) { throw "packaged launcher is missing: $launcher" }
if (-not (Test-Path $shippedReasoning)) {
    throw "packaged shipped reasoning directory is missing: $shippedReasoning"
}

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
    New-Item -ItemType Directory -Force -Path $env:HOME | Out-Null
    if ((Test-Path $ownerReasoning) -and @(Get-ChildItem $ownerReasoning -Filter '*.jar').Count -ne 0) {
        throw 'owner reasoning directory was not initially empty'
    }

    $shippedHashes = @{}
    foreach ($jar in @(Get-ChildItem $shippedReasoning -Filter '*.jar')) {
        $shippedHashes[$jar.FullName] = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash
    }
    if ($shippedHashes.Count -lt 1) { throw 'native image contains no shipped reasoning JARs' }

    $install = Invoke-Madre @('reasoning', 'install', $fixture)
    if ($install -notmatch 'reasoning\.provider\.installed\s+independent-text' -or
            $install -notmatch 'source=owner') {
        throw 'product reasoning install did not report the independent provider as owner-installed'
    }
    $ownerArtifacts = @(Get-ChildItem $ownerReasoning -Filter '*.jar')
    if ($ownerArtifacts.Count -ne 1) {
        throw 'managed reasoning install did not create exactly one owner JAR'
    }

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
    if ($providers -notmatch 'reasoning\.provider\s+independent-text\s+Independent deterministic text.*source=owner') {
        throw 'generic provider discovery did not render independent owner source metadata'
    }
    if ($providers -notmatch 'field\s+privacy\s+CHOICE\s+required') {
        throw 'generic provider discovery did not render provider-owned field metadata'
    }

    # Managed replacement is same-identity only and commits staged bytes after provider validation.
    $replacementJar = Join-Path $temp 'independent-reasoning-replacement.jar'
    Copy-Item $fixture $replacementJar
    $replacementStream = [System.IO.File]::Open($replacementJar,
        [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
    try {
        $archive = [System.IO.Compression.ZipArchive]::new(
            $replacementStream, [System.IO.Compression.ZipArchiveMode]::Update, $true)
        try {
            $entry = $archive.CreateEntry('META-INF/madre-replacement-marker.txt')
            $writer = [System.IO.StreamWriter]::new($entry.Open())
            try { $writer.Write('replacement') } finally { $writer.Dispose() }
        } finally {
            $archive.Dispose()
        }
    } finally {
        $replacementStream.Dispose()
    }
    $managedPath = $ownerArtifacts[0].FullName
    $oldManagedHash = (Get-FileHash $managedPath -Algorithm SHA256).Hash
    $replace = Invoke-Madre @('reasoning', 'install', $replacementJar, '--replace')
    if ($replace -notmatch 'reasoning\.provider\.replaced\s+independent-text') {
        throw 'explicit reasoning-provider replacement was not reported'
    }
    $afterReplacement = @(Get-ChildItem $ownerReasoning -Filter '*.jar')
    if ($afterReplacement.Count -ne 1 -or $afterReplacement[0].FullName -ne $managedPath) {
        throw 'reasoning replacement changed the managed canonical artifact slot'
    }
    $newManagedHash = (Get-FileHash $managedPath -Algorithm SHA256).Hash
    if ($newManagedHash -eq $oldManagedHash -or
            $newManagedHash -ne (Get-FileHash $replacementJar -Algorithm SHA256).Hash) {
        throw 'reasoning replacement did not commit the supplied staged bytes'
    }

    # Every shipped provider is protected from owner uninstallation; shipped bytes remain unchanged.
    $shippedProviderMatches = [regex]::Matches($providers,
        '(?m)^reasoning\.provider\s+([^\s]+).*source=shipped')
    if ($shippedProviderMatches.Count -lt 1) {
        throw 'provider listing did not expose any shipped provider classification'
    }
    foreach ($match in $shippedProviderMatches) {
        $providerId = $match.Groups[1].Value
        $failure = Invoke-MadreFailure @('reasoning', 'uninstall', $providerId)
        if ($failure -notmatch 'shipped reasoning provider cannot be uninstalled') {
            throw "shipped reasoning provider $providerId was not protected from owner uninstall"
        }
    }
    $shippedCandidate = @(Get-ChildItem $shippedReasoning -Filter '*.jar')[0]
    $collision = Invoke-MadreFailure @('reasoning', 'install', $shippedCandidate.FullName)
    if ($collision -notmatch 'shipped with MADRE' -or $collision -notmatch 'cannot be overridden') {
        throw 'reasoning install did not reject a shipped canonical identity collision'
    }
    foreach ($path in $shippedHashes.Keys) {
        if ((Get-FileHash $path -Algorithm SHA256).Hash -ne $shippedHashes[$path]) {
            throw "shipped reasoning artifact changed during rejected lifecycle operations: $path"
        }
    }

    Invoke-Madre @('reasoning', 'configure', 'independent-text', 'preserved',
        '--set', 'capability-id=independent-preserved', '--set', 'privacy=SECRET',
        '--set', 'location=LOCAL') | Out-Null
    Invoke-Madre @('reasoning', 'disable', 'independent-text/preserved') | Out-Null

    Invoke-Madre @('reasoning', 'configure', 'independent-text', 'deterministic',
        '--set', 'capability-id=independent-text', '--set', 'privacy=SECRET',
        '--set', 'location=LOCAL') | Out-Null
    Add-Content -Path $configuration -Encoding utf8 `
        -Value 'modules.config[io.github.didacll.madre.owner-interaction].foreground-maximum-tokens=37'
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
    if (-not (Test-Path $managedPath)) {
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
    if (-not (Test-Path $managedPath)) {
        throw 'removing provider instance configuration removed its installed provider artifact'
    }

    # Provider artifact uninstall remains separate from reasoning remove and is configuration-safe.
    $plainUninstall = Invoke-MadreFailure @('reasoning', 'uninstall', 'independent-text')
    if ($plainUninstall -notmatch 'has configured instances' -or -not (Test-Path $managedPath)) {
        throw 'plain provider uninstall did not refuse while configured instances remained'
    }
    Invoke-Madre @('reasoning', 'uninstall', 'independent-text', '--purge-configuration') | Out-Null
    if (Test-Path $managedPath) {
        throw 'provider purge uninstall did not remove the owner-managed reasoning artifact'
    }
    $afterPurgeText = Get-Content $configuration -Raw
    if ($afterPurgeText -match 'reasoning\.independent-text\.') {
        throw 'provider-owned configuration survived explicit purge uninstall'
    }
    if ($afterPurgeText -notmatch 'modules\.config\[io\.github\.didacll\.madre\.owner-interaction\]\.foreground-maximum-tokens=37') {
        throw 'reasoning provider purge changed unrelated Module configuration'
    }
    $providersAfterPurge = Invoke-Madre @('reasoning', 'providers')
    if ($providersAfterPurge -match 'reasoning\.provider\s+independent-text') {
        throw 'uninstalled independent reasoning provider remained discoverable'
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
