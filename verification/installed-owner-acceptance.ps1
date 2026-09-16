param(
    [Parameter(Mandatory = $true)]
    [string]$AppImageRoot,
    [Parameter(Mandatory = $true)]
    [string]$ModuleJar,
    [Parameter(Mandatory = $true)]
    [string]$ReplacementModuleJar,
    [string]$ReasoningJar,
    [string]$InitialBehavior = 'v1',
    [string]$ReplacementBehavior = 'v2'
)

$ErrorActionPreference = 'Stop'

$root = (Get-Location).Path
$rootPath = [IO.Path]::GetFullPath($root)
$separator = [IO.Path]::DirectorySeparatorChar.ToString()
$rootPrefix = if ($rootPath.EndsWith($separator)) { $rootPath } else { $rootPath + $separator }

function Resolve-ExternalModuleArtifact([string]$path, [string]$label) {
    if (-not (Test-Path -LiteralPath $path)) { throw "$label is missing: $path" }
    $full = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $path).Path)
    if ($full.Equals($rootPath, [StringComparison]::OrdinalIgnoreCase) -or
            $full.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$label must be built outside the MADRE checkout: $full"
    }
    return $full
}

$appImage = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $AppImageRoot).Path)
$independentModule = Resolve-ExternalModuleArtifact $ModuleJar 'independent Module'
$replacementModule = Resolve-ExternalModuleArtifact $ReplacementModuleJar 'replacement Module'
if ([string]::IsNullOrWhiteSpace($ReasoningJar)) {
    $ReasoningJar = Join-Path $root 'verification/reasoning-consumer/build/libs/independent-reasoning.jar'
}
$independentReasoning = [IO.Path]::GetFullPath($ReasoningJar)
$ownerModuleId = 'io.github.didacll.madre.owner-interaction'

if (-not (Test-Path $independentReasoning)) { throw 'independent reasoning fixture is missing' }
if ((Get-FileHash -LiteralPath $independentModule -Algorithm SHA256).Hash -eq
        (Get-FileHash -LiteralPath $replacementModule -Algorithm SHA256).Hash) {
    throw 'initial and replacement Module artifacts are byte-identical'
}

if ($IsWindows) {
    $launcher = Join-Path $appImage 'madre.exe'
    $appDirectory = Join-Path $appImage 'app'
} else {
    $launcher = Join-Path $appImage 'bin/madre'
    $appDirectory = Join-Path $appImage 'lib/app'
}
$moduleDir = Join-Path $appDirectory 'modules'
$reasoningDir = Join-Path $appDirectory 'reasoning'
$defaults = Join-Path $appDirectory 'defaults/madre.properties'
if (-not (Test-Path $launcher)) { throw "packaged MADRE launcher is missing: $launcher" }
if (-not (Test-Path $defaults)) { throw 'owner app image is missing packaged first-run defaults' }
if (@(Get-ChildItem $moduleDir -Filter 'madre-module-owner-interaction*.jar').Count -lt 1) {
    throw 'shipped owner-interaction Module is missing from the owner app image'
}
if (@(Get-ChildItem $reasoningDir -Filter 'madre-adapter-openai-compatible*.jar').Count -lt 1) {
    throw 'shipped OpenAI-compatible adapter is missing from the owner app image'
}

$temp = Join-Path ([IO.Path]::GetTempPath()) ('madre-installed-extension-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null
$oldHome = $env:HOME
$oldAppData = $env:APPDATA
$oldLocalAppData = $env:LOCALAPPDATA
$oldXdgConfig = $env:XDG_CONFIG_HOME
$oldXdgData = $env:XDG_DATA_HOME
$oldXdgState = $env:XDG_STATE_HOME
$env:HOME = Join-Path $temp 'home'
if ($IsWindows) {
    $env:APPDATA = Join-Path $temp 'roaming'
    $env:LOCALAPPDATA = Join-Path $temp 'local'
    $configuration = Join-Path $env:APPDATA 'MADRE/madre.properties'
    $ownerModuleDir = Join-Path $env:LOCALAPPDATA 'MADRE/modules'
    $ownerReasoningDir = Join-Path $env:LOCALAPPDATA 'MADRE/reasoning'
} else {
    $env:XDG_CONFIG_HOME = Join-Path $temp 'xdg-config'
    $env:XDG_DATA_HOME = Join-Path $temp 'xdg-data'
    $env:XDG_STATE_HOME = Join-Path $temp 'xdg-state'
    $configuration = Join-Path $env:XDG_CONFIG_HOME 'madre/madre.properties'
    $ownerModuleDir = Join-Path $env:XDG_DATA_HOME 'madre/modules'
    $ownerReasoningDir = Join-Path $env:XDG_DATA_HOME 'madre/reasoning'
}

function Invoke-Madre([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { Write-Host $output; throw "MADRE invocation failed with $LASTEXITCODE" }
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

function Invoke-Console([string[]]$lines) {
    $input = ($lines -join [Environment]::NewLine) + [Environment]::NewLine
    $output = ($input | & $launcher 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { Write-Host $output; throw "MADRE console failed with $LASTEXITCODE" }
    Write-Host $output
    return $output
}

try {
    if (Test-Path $configuration) { throw 'fresh owner configuration unexpectedly exists' }
    if ((Test-Path $ownerModuleDir) -and @(Get-ChildItem $ownerModuleDir -Filter '*.jar').Count -ne 0) {
        throw 'owner Module directory was not initially empty'
    }
    if ((Test-Path $ownerReasoningDir) -and @(Get-ChildItem $ownerReasoningDir -Filter '*.jar').Count -ne 0) {
        throw 'owner reasoning directory was not initially empty'
    }

    # The app image was already built before either external Module artifact. Installing the first
    # artifact through the ordinary owner command also exercises packaged first-run configuration.
    $moduleInstall = Invoke-Madre @('modules', 'install', $independentModule)
    if ($moduleInstall -notmatch 'module\.installed\s+phd\.module' -or $moduleInstall -notmatch 'source=owner') {
        throw 'independent Module was not installed as an owner artifact'
    }
    if (-not (Test-Path $configuration)) {
        throw 'normal owner lifecycle did not bootstrap persistent owner configuration'
    }
    $moduleList = Invoke-Madre @('modules', 'list')
    if ($moduleList -notmatch 'module\s+phd\.module.*source=owner') {
        throw 'Module list did not expose independent owner source metadata'
    }
    Invoke-Madre @('modules', 'configure', 'phd.module', '--set', 'result-prefix=configured-') | Out-Null

    # External/public disclosure and generic owner/debug entry are independent mechanical receiver
    # paths. They prove installed execution without pretending the agentless target owns semantic intent.
    $initialPublicExpected = "public:${InitialBehavior}:configured-hello"
    $public = Invoke-Madre @('--invoke-public', 'phd.module', 'inspect', 'request', 'S4', 'hello')
    if ($public -notmatch [regex]::Escape($initialPublicExpected)) {
        throw "initial independent Module behavior was not observed: $initialPublicExpected"
    }
    $initialOwnerExpected = "private:${InitialBehavior}:configured-hello"
    $owner = Invoke-Madre @('--invoke-owner', 'phd.module', 'inspect', 'request', 'S4', 'hello')
    if ($owner -notmatch ('S4\s+' + [regex]::Escape($initialOwnerExpected))) {
        throw "generic owner/debug entry did not execute initial installed behavior: $initialOwnerExpected"
    }

    # Replace the managed owner artifact with a separately built same-identity artifact. Configuration
    # is host-owned installation state, so it must survive byte replacement and reach the new Module.
    $moduleReplace = Invoke-Madre @('modules', 'install', $replacementModule, '--replace')
    if ($moduleReplace -notmatch 'module\.replaced\s+phd\.module' -or
            $moduleReplace -notmatch 'source=owner') {
        throw 'independent Module replacement was not committed as an owner artifact'
    }
    $moduleInspect = Invoke-Madre @('modules', 'inspect', 'phd.module')
    if ($moduleInspect -notmatch [regex]::Escape('current=configured-')) {
        throw 'Module configuration did not survive artifact replacement'
    }

    $replacementPublicExpected = "public:${ReplacementBehavior}:configured-hello"
    $replacementPublic = Invoke-Madre @('--invoke-public', 'phd.module', 'inspect', 'request', 'S4', 'hello')
    if ($replacementPublic -notmatch [regex]::Escape($replacementPublicExpected)) {
        throw "replacement behavior was not observed: $replacementPublicExpected"
    }
    if ($replacementPublic -match [regex]::Escape($initialPublicExpected)) {
        throw 'replacement execution still exposed initial artifact behavior'
    }
    $replacementOwnerExpected = "private:${ReplacementBehavior}:configured-hello"
    $replacementOwner = Invoke-Madre @('--invoke-owner', 'phd.module', 'inspect', 'request', 'S4', 'hello')
    if ($replacementOwner -notmatch ('S4\s+' + [regex]::Escape($replacementOwnerExpected))) {
        throw "generic owner/debug entry did not execute replacement behavior: $replacementOwnerExpected"
    }

    # Independently compiled provider lifecycle remains separate from Module installation.
    $providerInstall = Invoke-Madre @('reasoning', 'install', $independentReasoning)
    if ($providerInstall -notmatch 'reasoning\.provider\.installed\s+independent-text' -or
            $providerInstall -notmatch 'source=owner') {
        throw 'independent reasoning provider was not installed as an owner artifact'
    }
    $providers = Invoke-Madre @('reasoning', 'providers')
    if ($providers -notmatch 'reasoning\.provider\s+independent-text.*source=owner') {
        throw 'provider listing did not expose independent owner source metadata'
    }
    Invoke-Madre @('reasoning', 'configure', 'independent-text', 'deterministic',
        '--set', 'capability-id=independent-text', '--set', 'privacy=SECRET',
        '--set', 'location=LOCAL') | Out-Null
    $doctor = Invoke-Madre @('doctor')
    if ($doctor -notmatch 'core\s+resolved io\.github\.didacll\.madre\.owner-interaction') {
        throw 'selected ordinary CORE Module did not resolve'
    }
    if ($doctor -notmatch 'reasoning\.mechanism\s+independent-text') {
        throw 'configured independent reasoning mechanism did not materialize'
    }

    # Owner interaction is not external/public disclosure. Without a public transformation the
    # external receiver must reject the same Operation even though the selected CORE host can enter it.
    $publicFailure = Invoke-MadreFailure @('--invoke-public', $ownerModuleId,
        'standard-prompt', 'owner-prompt', 'S5', 'must-not-be-public')
    if ($publicFailure -notmatch 'external/public disclosure transformation') {
        throw 'external public path did not require an explicit disclosure transformation'
    }

    # The ordinary console reaches the selected Module's explicit interaction entry and the real
    # OperationCall/ReasoningService path still carries S5 Material to the SECRET fixture.
    $console = Invoke-Console @('/standard sdk-owner', '/exit')
    if ($console -notmatch 'S5\s+independent:sdk-owner') {
        throw 'ordinary owner interaction did not execute through the configured reasoning mechanism'
    }

    # Shipped artifacts remain immutable through owner lifecycle operations.
    $shippedUninstall = Invoke-MadreFailure @('modules', 'uninstall', $ownerModuleId)
    if ($shippedUninstall -notmatch 'shipped Module cannot be uninstalled') {
        throw 'shipped owner-interaction Module was not protected from uninstall'
    }

    Invoke-Madre @('reasoning', 'disable', 'independent-text/deterministic') | Out-Null
    $disabled = Invoke-Madre @('doctor')
    if ($disabled -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'disabled independent provider instance still materialized'
    }
    Invoke-Madre @('reasoning', 'enable', 'independent-text/deterministic') | Out-Null
    $reenabled = Invoke-Madre @('doctor')
    if ($reenabled -notmatch 'reasoning\.mechanisms\.count\s+1') {
        throw 'generic provider enable did not re-materialize the independent mechanism'
    }
} finally {
    $env:HOME = $oldHome
    $env:APPDATA = $oldAppData
    $env:LOCALAPPDATA = $oldLocalAppData
    $env:XDG_CONFIG_HOME = $oldXdgConfig
    $env:XDG_DATA_HOME = $oldXdgData
    $env:XDG_STATE_HOME = $oldXdgState
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}
