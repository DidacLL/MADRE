param()

$ErrorActionPreference = 'Stop'

function Slash([string]$value) { return $value.Replace('\', '/') }

$root = (Get-Location).Path
$dist = Join-Path $root 'madre-app/build/install/madre'
$moduleDir = Join-Path $dist 'modules'
$reasoningDir = Join-Path $dist 'reasoning'
$independentModule = Join-Path $root 'verification/sdk-consumer/build/libs/independent-module.jar'
$independentReasoning = Join-Path $root 'verification/reasoning-consumer/build/libs/independent-reasoning.jar'
$ownerModuleId = 'io.github.didacll.madre.owner-interaction'
$ownerForegroundTokens = 37
$ownerBackgroundTokens = 41

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

function Write-Config {
    param(
        [string]$path,
        [string]$database,
        [string]$stateDirectory,
        [string]$gateFile = '',
        [string]$completionFile = '',
        [string]$coreModule = '',
        [bool]$interaction = $true,
        [bool]$reasoning = $true,
        [string]$configuredReasoningDir = '',
        [bool]$ownerModuleConfiguration = $true,
        [string]$independentResultPrefix = ''
    )
    if ([string]::IsNullOrWhiteSpace($configuredReasoningDir)) {
        $configuredReasoningDir = $reasoningDir
    }
    $lines = @(
        "kernel.database=$(Slash $database)",
        "modules.directory=$(Slash $moduleDir)",
        "modules.state-directory=$(Slash $stateDirectory)",
        "reasoning.directory=$(Slash $configuredReasoningDir)"
    )
    if ($ownerModuleConfiguration) {
        $lines += @(
            "modules.config[$ownerModuleId].foreground-maximum-tokens=$ownerForegroundTokens",
            "modules.config[$ownerModuleId].background-maximum-tokens=$ownerBackgroundTokens"
        )
    }
    if (-not [string]::IsNullOrWhiteSpace($independentResultPrefix)) {
        $lines += "modules.config[phd.module].result-prefix=$independentResultPrefix"
    }
    if ($reasoning) {
        $lines += @(
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
    }
    if ($interaction) {
        $lines += @(
            'interaction.module=io.github.didacll.madre.owner-interaction',
            'interaction.default-operation=fast-lane',
            'interaction.standard-operation=standard-prompt',
            'interaction.prompt-material-type=owner-prompt',
            'interaction.default-sensitivity=S5',
            'interaction.updates-operation=collect-background',
            'interaction.updates-material-type=background-collection-request',
            'interaction.updates-payload=collect',
            'interaction.updates-sensitivity=S1'
        )
    }
    if ($coreModule) {
        $lines += "roles.core=$coreModule"
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

function Invoke-MadreConsole([string]$config, [string[]]$inputLines) {
    $inputText = ($inputLines -join [Environment]::NewLine) + [Environment]::NewLine
    $output = ($inputText | & $launcher $config 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        Write-Host $output
        throw "MADRE console failed with exit code $LASTEXITCODE"
    }
    if ($output -match 'RejectedExecutionException') {
        Write-Host $output
        throw 'MADRE console emitted a reasoning-scheduler shutdown race'
    }
    Write-Host $output
    return $output
}

function Invoke-MadreExpectFailure([string]$config, [string[]]$arguments) {
    $output = (& $launcher $config @arguments 2>&1 | Out-String).Trim()
    $status = $LASTEXITCODE
    Write-Host $output
    if ($status -eq 0) { throw 'MADRE invocation unexpectedly succeeded' }
    return $output
}

try {
    # Independently compiled SDK consumer: omitted configuration keeps its documented old behavior.
    $independentDefaultConfig = Join-Path $temp 'independent-default.properties'
    Write-Config -path $independentDefaultConfig -database (Join-Path $temp 'independent-default.sqlite') `
        -stateDirectory (Join-Path $temp 'independent-default-state') -interaction $false `
        -reasoning $false -ownerModuleConfiguration $false
    $independentDefault = Invoke-Madre $independentDefaultConfig @(
        '--invoke-public', 'phd.module', 'inspect', 'request', 'S4', 'hello')
    if ($independentDefault -notmatch 'public:hello') {
        throw 'independent Module omitted configuration did not preserve deterministic default behavior'
    }

    # The same external provider consumes identity-scoped owner configuration through the SDK boundary.
    $independentConfiguredConfig = Join-Path $temp 'independent-configured.properties'
    Write-Config -path $independentConfiguredConfig `
        -database (Join-Path $temp 'independent-configured.sqlite') `
        -stateDirectory (Join-Path $temp 'independent-configured-state') -interaction $false `
        -reasoning $false -ownerModuleConfiguration $false -independentResultPrefix 'configured-'
    $independentConfigured = Invoke-Madre $independentConfiguredConfig @(
        '--invoke-public', 'phd.module', 'inspect', 'request', 'S4', 'hello')
    if ($independentConfigured -notmatch 'public:configured-hello') {
        throw 'independent Module did not receive non-default owner installation configuration'
    }

    # Omitted shipped-Module configuration preserves the pre-existing reasoning defaults.
    $ownerDefaultConfig = Join-Path $temp 'owner-default.properties'
    Write-Config -path $ownerDefaultConfig -database (Join-Path $temp 'owner-default.sqlite') `
        -stateDirectory (Join-Path $temp 'owner-default-state') -ownerModuleConfiguration $false
    $ownerDefault = Invoke-Madre $ownerDefaultConfig @(
        '--invoke-owner', $ownerModuleId, 'standard-prompt', 'owner-prompt', 'S5', 'owner-default')
    if ($ownerDefault -notmatch 'S5\s+independent:owner-default\|maximum-generated-tokens=256') {
        throw 'omitted owner-interaction Module configuration did not preserve current defaults'
    }

    # Keep the exact generic owner-local and PUBLIC diagnostic boundaries independently proven.
    # Non-default Module-owned reasoning limits must reach the actual independent reasoning mechanism.
    $standardState = Join-Path $temp 'standard-state'
    $standardDb = Join-Path $temp 'standard.sqlite'
    $standardConfig = Join-Path $temp 'standard.properties'
    Write-Config -path $standardConfig -database $standardDb -stateDirectory $standardState

    $owner = Invoke-Madre $standardConfig @(
        '--invoke-owner', $ownerModuleId, 'standard-prompt',
        'owner-prompt', 'S5', 'sensitive-owner-material')
    if ($owner -notmatch 'S5\s+independent:sensitive-owner-material\|maximum-generated-tokens=37') {
        throw 'owner-interaction foreground Module configuration did not reach real reasoning execution'
    }

    $public = Invoke-Madre $standardConfig @(
        '--invoke-public', $ownerModuleId, 'standard-prompt',
        'owner-prompt', 'S5', 'sensitive-owner-material')
    if ($public -notmatch 'owner-interaction result withheld at public boundary') {
        throw 'PUBLIC standard-prompt did not apply semantic minimization'
    }
    if ($public -match 'independent:sensitive-owner-material') {
        throw 'sensitive owner reasoning leaked through PUBLIC invocation'
    }

    # The configured presentation uses the same installed Module through owner-local invocation.
    $console = Invoke-MadreConsole $standardConfig @(
        'ordinary-default',
        '/sensitivity S3',
        'ordinary-session',
        '/standard explicit-standard',
        '/exit')
    if ($console -notmatch 'S5\s+independent:ordinary-default\|maximum-generated-tokens=37') {
        throw 'ordinary console text did not use configured owner-interaction reasoning limits'
    }
    if ($console -notmatch 'input sensitivity\s+S3') {
        throw 'explicit session sensitivity command was not applied'
    }
    if ($console -notmatch 'S3\s+independent:ordinary-session\|maximum-generated-tokens=37') {
        throw 'ordinary console text did not preserve explicit session Sensitivity'
    }
    if ($console -notmatch 'S3\s+independent:explicit-standard\|maximum-generated-tokens=37') {
        throw '/standard did not execute the configured standard owner-local path'
    }

    # CORE assignment to the interaction Module is an independent role fact.
    $coreState = Join-Path $temp 'core-state'
    $coreDb = Join-Path $temp 'core.sqlite'
    $coreConfig = Join-Path $temp 'core.properties'
    Write-Config -path $coreConfig -database $coreDb -stateDirectory $coreState `
        -coreModule $ownerModuleId
    $modules = Invoke-Madre $coreConfig @('--list-modules')
    if ($modules -notmatch 'io\.github\.didacll\.madre\.owner-interaction\s+1\.1\.0\s+\[CORE\]') {
        throw 'owner-interaction Module did not resolve as optional CORE role'
    }
    $coreConsole = Invoke-MadreConsole $coreConfig @('core-ordinary', '/exit')
    if ($coreConsole -notmatch 'S5\s+independent:core-ordinary\|maximum-generated-tokens=37') {
        throw 'CORE assignment changed Module configuration or convenient owner-local interaction'
    }

    # A different resolved CORE and an unresolved CORE likewise do not control configuration/presentation.
    $differentCoreConfig = Join-Path $temp 'different-core.properties'
    Write-Config -path $differentCoreConfig -database (Join-Path $temp 'different-core.sqlite') `
        -stateDirectory (Join-Path $temp 'different-core-state') -coreModule 'phd.module'
    $differentModules = Invoke-Madre $differentCoreConfig @('--list-modules')
    if ($differentModules -notmatch 'phd\.module\s+1\.0\.0\s+\[CORE\]') {
        throw 'independent Module did not resolve as the deliberately different CORE'
    }
    $differentCoreConsole = Invoke-MadreConsole $differentCoreConfig @('different-core', '/exit')
    if ($differentCoreConsole -notmatch 'S5\s+independent:different-core\|maximum-generated-tokens=37') {
        throw 'different CORE assignment changed owner-interaction Module configuration semantics'
    }

    $unresolvedCoreConfig = Join-Path $temp 'unresolved-core.properties'
    Write-Config -path $unresolvedCoreConfig -database (Join-Path $temp 'unresolved-core.sqlite') `
        -stateDirectory (Join-Path $temp 'unresolved-core-state') -coreModule 'missing.module'
    $unresolvedCoreConsole = Invoke-MadreConsole $unresolvedCoreConfig @('unresolved-core', '/exit')
    if ($unresolvedCoreConsole -notmatch 'S5\s+independent:unresolved-core\|maximum-generated-tokens=37') {
        throw 'unresolved CORE assignment changed owner-interaction Module configuration semantics'
    }

    # No interaction configuration remains valid and does not alter the Module configuration boundary.
    $genericConfig = Join-Path $temp 'generic.properties'
    Write-Config -path $genericConfig -database (Join-Path $temp 'generic.sqlite') `
        -stateDirectory (Join-Path $temp 'generic-state') -interaction $false
    $genericConsole = Invoke-MadreConsole $genericConfig @('/modules', '/exit')
    if ($genericConsole -notmatch 'MADRE ready - generic console') {
        throw 'no-interaction installation did not boot into the generic console'
    }
    if ($genericConsole -notmatch 'io\.github\.didacll\.madre\.owner-interaction\s+1\.1\.0') {
        throw 'generic console lost normal installed Module discovery'
    }
    $genericOwner = Invoke-Madre $genericConfig @(
        '--invoke-owner', $ownerModuleId, 'standard-prompt', 'owner-prompt', 'S5', 'generic-config')
    if ($genericOwner -notmatch 'S5\s+independent:generic-config\|maximum-generated-tokens=37') {
        throw 'interaction presentation binding affected Module configuration semantics'
    }

    # Explicit malformed Module-owned configuration fails startup rather than falling back.
    $invalidModuleConfig = Join-Path $temp 'invalid-module.properties'
    Write-Config -path $invalidModuleConfig -database (Join-Path $temp 'invalid-module.sqlite') `
        -stateDirectory (Join-Path $temp 'invalid-module-state')
    Add-Content -Path $invalidModuleConfig `
        -Value "modules.config[$ownerModuleId].foreground-maximum-tokens=not-a-number"
    $invalidModule = Invoke-MadreExpectFailure $invalidModuleConfig @('--list-modules')
    if ($invalidModule -notmatch 'cannot materialize Module io\.github\.didacll\.madre\.owner-interaction' `
            -or $invalidModule -notmatch 'foreground-maximum-tokens must be an integer') {
        throw 'malformed explicit Module configuration did not fail startup clearly'
    }

    # Interaction validation happens separately at startup against installed canonical declarations.
    $invalidConfig = Join-Path $temp 'invalid-interaction.properties'
    Copy-Item $standardConfig $invalidConfig
    Add-Content -Path $invalidConfig -Value 'interaction.default-operation=missing-operation'
    $invalid = Invoke-MadreExpectFailure $invalidConfig @('--list-modules')
    if ($invalid -notmatch 'interaction\.default-operation is incompatible') {
        throw 'malformed interaction binding did not fail startup clearly'
    }

    # Reasoning is not a boot requirement; a reasoning-backed interaction fails as an operation.
    $emptyReasoning = Join-Path $temp 'empty-reasoning'
    New-Item -ItemType Directory -Force -Path $emptyReasoning | Out-Null
    $noReasoningConfig = Join-Path $temp 'no-reasoning-interaction.properties'
    Write-Config -path $noReasoningConfig -database (Join-Path $temp 'no-reasoning.sqlite') `
        -stateDirectory (Join-Path $temp 'no-reasoning-state') -reasoning $false `
        -configuredReasoningDir $emptyReasoning
    $noReasoningConsole = Invoke-MadreConsole $noReasoningConfig @(
        'reasoning-unavailable', '/modules', '/exit')
    if ($noReasoningConsole -notmatch 'operation failure:') {
        throw 'missing reasoning mechanism was not reported as an operation/runtime failure'
    }
    if ($noReasoningConsole -notmatch 'io\.github\.didacll\.madre\.owner-interaction\s+1\.1\.0') {
        throw 'console did not remain usable after reasoning availability failure'
    }

    # Presentation-level durable restart proof: ordinary text -> exit -> deterministic completion -> /updates.
    $restartState = Join-Path $temp 'restart-state'
    $restartDb = Join-Path $temp 'restart.sqlite'
    $restartConfig = Join-Path $temp 'restart.properties'
    $gate = Join-Path $temp 'background.gate'
    $completion = Join-Path $temp 'background.complete'
    Write-Config -path $restartConfig -database $restartDb -stateDirectory $restartState `
        -gateFile $gate -completionFile $completion

    $foreground = Invoke-MadreConsole $restartConfig @('restart-sensitive', '/exit')
    if ($foreground -notmatch 'S5\s+independent:restart-sensitive\|maximum-generated-tokens=37') {
        throw 'fast-lane foreground did not use configured Module reasoning limit'
    }
    if (Test-Path $completion) {
        throw 'background reasoning completed before the deterministic restart gate opened'
    }
    $pendingState = Join-Path $restartState 'owner-interaction-background.state'
    if (-not (Test-Path $pendingState) -or (Get-Item $pendingState).Length -eq 0) {
        throw 'Module-owned pending background state was not persisted before restart'
    }

    Set-Content -Path $gate -Value 'open' -Encoding utf8
    # Keep the previously accepted process boundary between the deterministic capability barrier
    # and collection so Kernel has persisted SUCCEEDED before the destructive collection Operation.
    $barrier = Invoke-MadreConsole $restartConfig @(
        '/standard await-background-completion',
        '/exit')
    if ($barrier -notmatch 'S5\s+independent:await-background-completion\|maximum-generated-tokens=37') {
        throw 'restart completion barrier did not retain configured foreground reasoning limit'
    }
    if (-not (Test-Path $completion)) {
        throw 'durable reasoning did not resume and complete after restart'
    }
    if ((Get-Item $pendingState).Length -eq 0) {
        throw 'Module-owned pending state disappeared before explicit /updates acknowledgement'
    }

    $restarted = Invoke-MadreConsole $restartConfig @(
        '/updates',
        '/updates',
        '/exit')
    if ($restarted -notmatch '(?s)S5\s+[^\r\n]*independent:background-useful:.*\|maximum-generated-tokens=41') {
        throw '/updates did not prove configured background Module reasoning limit reached durable execution'
    }
    if ((Get-Item $pendingState).Length -ne 0) {
        throw 'Module-owned pending state was not removed after /updates collection'
    }
    if ($restarted -notmatch 'S1\s+no completed background updates') {
        throw 'second /updates did not prove acknowledgement and cleanup'
    }
} finally {
    Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
}
