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
    if ($first -notmatch 'No reasoning mechanisms are enabled') {
        throw 'fresh zero-reasoning startup did not point the owner toward generic reasoning setup'
    }
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
    $doctor = Invoke-Madre @('doctor')
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
    if ($doctor -notmatch 'reasoning\.providers\.count\s+3' -or
            $doctor -notmatch 'reasoning\.provider\s+llamacpp-http' -or
            $doctor -notmatch 'reasoning\.provider\s+llamacpp-unix' -or
            $doctor -notmatch 'reasoning\.provider\s+openai-compatible') {
        throw 'doctor did not distinguish the shipped configurable provider types'
    }
    if ($doctor -notmatch 'reasoning\.instances\.count\s+0' -or
            $doctor -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'fresh install silently configured or materialized a reasoning mechanism'
    }
    if ($doctor -notmatch [regex]::Escape((Resolve-Path $runtime).Path)) {
        throw 'doctor does not show the packaged Java runtime as java.home'
    }
    if ($doctor -match 'reasoning\..*(endpoint|model|credential|token|secret|privacy|latency|preference)') {
        throw 'doctor exposed provider configuration details'
    }

    $doctorAgain = Invoke-Madre @('doctor')
    $configurationAfter = (Get-FileHash $configuration -Algorithm SHA256).Hash
    if ($configurationAfter -ne $configurationBefore) {
        throw 'second launch rewrote established owner configuration'
    }
    if ($doctorAgain -notmatch [regex]::Escape($configuration)) {
        throw 'second launch did not resolve the same owner configuration location'
    }

    $providers = Invoke-Madre @('reasoning', 'providers')
    if ($providers -notmatch 'reasoning\.provider\s+llamacpp-http\s+llama\.cpp loopback HTTP' -or
            $providers -notmatch 'field\s+endpoint\s+TEXT\s+required') {
        throw 'shipped llama.cpp HTTP provider did not expose provider-owned generic metadata'
    }
    if ($providers -notmatch 'reasoning\.provider\s+llamacpp-unix\s+llama\.cpp Unix socket' -or
            $providers -notmatch 'field\s+socket\s+TEXT\s+required') {
        throw 'shipped llama.cpp Unix provider did not expose its distinct configuration shape'
    }
    if ($providers -notmatch 'reasoning\.provider\s+openai-compatible\s+OpenAI-compatible HTTP' -or
            $providers -notmatch 'field\s+location\s+CHOICE\s+required') {
        throw 'shipped OpenAI-compatible provider did not expose provider-owned generic metadata'
    }

    Invoke-Madre @('reasoning', 'configure', 'openai-compatible', 'preserved',
        '--set', 'capability-id=packaged-compatible',
        '--set', 'endpoint=http://127.0.0.1:65534/v1/',
        '--set', 'model=acceptance-model', '--set', 'privacy=SECRET', '--set', 'location=LOCAL') | Out-Null
    Invoke-Madre @('reasoning', 'disable', 'openai-compatible/preserved') | Out-Null

    Invoke-Madre @('reasoning', 'configure', 'llamacpp-http', 'packaged-probe',
        '--set', 'capability-id=packaged-llama',
        '--set', 'endpoint=http://127.0.0.1:65535/',
        '--set', 'model=acceptance-model', '--set', 'privacy=SECRET', '--set', 'model-slot-units=1') | Out-Null

    $validHash = (Get-FileHash $configuration -Algorithm SHA256).Hash
    $invalid = Invoke-MadreFailure @('reasoning', 'configure', 'llamacpp-http', 'packaged-probe',
        '--set', 'endpoint=https://example.com/')
    if ($invalid -notmatch 'loopback') {
        throw 'invalid shipped-provider input did not return provider-owned validation'
    }
    if ((Get-FileHash $configuration -Algorithm SHA256).Hash -ne $validHash) {
        throw 'failed provider validation changed the persisted owner configuration'
    }

    $inspect = Invoke-Madre @('reasoning', 'inspect', 'llamacpp-http/packaged-probe')
    if ($inspect -notmatch 'reasoning\.state\s+enabled' -or
            $inspect -notmatch 'reasoning\.field\s+privacy\s+SECRET') {
        throw 'generic inspection did not expose configured shipped-provider state'
    }
    if ($inspect -match 'reasoning\.llamacpp-http\.') {
        throw 'generic inspection leaked raw provider property names'
    }

    $configuredDoctor = Invoke-Madre @('doctor')
    if ($configuredDoctor -notmatch 'reasoning\.instance\s+llamacpp-http/packaged-probe\s+enabled' -or
            $configuredDoctor -notmatch 'reasoning\.instance\s+openai-compatible/preserved\s+disabled' -or
            $configuredDoctor -notmatch 'reasoning\.mechanisms\.count\s+1' -or
            $configuredDoctor -notmatch 'reasoning\.mechanism\s+packaged-llama') {
        throw 'configured shipped provider did not materialize after restart'
    }

    Invoke-Madre @('reasoning', 'disable', 'llamacpp-http/packaged-probe') | Out-Null
    $disabledHash = (Get-FileHash $configuration -Algorithm SHA256).Hash
    Invoke-Madre @('reasoning', 'disable', 'llamacpp-http/packaged-probe') | Out-Null
    if ((Get-FileHash $configuration -Algorithm SHA256).Hash -ne $disabledHash) {
        throw 'repeating an identical reasoning configuration update was not deterministic'
    }
    $disabledDoctor = Invoke-Madre @('doctor')
    if ($disabledDoctor -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'disabled shipped provider instance materialized after restart'
    }

    Invoke-Madre @('reasoning', 'enable', 'llamacpp-http/packaged-probe') | Out-Null
    $enabledDoctor = Invoke-Madre @('doctor')
    if ($enabledDoctor -notmatch 'reasoning\.mechanisms\.count\s+1') {
        throw 'generic enable did not re-materialize the shipped provider instance'
    }

    Invoke-Madre @('reasoning', 'remove', 'llamacpp-http/packaged-probe') | Out-Null
    $remaining = Invoke-Madre @('reasoning', 'list')
    if ($remaining -notmatch 'reasoning\.instances\.count\s+1' -or
            $remaining -notmatch 'openai-compatible/preserved\s+disabled' -or
            $remaining -match 'llamacpp-http/packaged-probe') {
        throw 'removal did not delete exactly the selected provider instance configuration'
    }
    $propertiesText = Get-Content $configuration -Raw
    if ($propertiesText -match 'reasoning\.llamacpp-http\.packaged-probe\.') {
        throw 'removed provider instance left owned raw configuration behind'
    }
    if ($propertiesText -notmatch 'roles\.core=io\.github\.didacll\.madre\.owner-interaction' -or
            $propertiesText -notmatch 'resources\.model-slot=1') {
        throw 'reasoning configuration updates discarded unrelated host configuration'
    }
    $afterRemoveDoctor = Invoke-Madre @('doctor')
    if ($afterRemoveDoctor -notmatch 'reasoning\.mechanisms\.count\s+0') {
        throw 'removing the enabled instance did not return materialization to zero'
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
