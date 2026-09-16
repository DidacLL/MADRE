param()

$ErrorActionPreference = 'Stop'

function Slash([string]$value) { return $value.Replace('\', '/') }

$root = (Get-Location).Path
$dist = Join-Path $root 'madre-app/build/install/madre'
$aaaatJar = @(Get-ChildItem (Join-Path $root 'madre-module-aaaat/build/libs') -Filter 'madre-module-aaaat*.jar') | Select-Object -First 1
$callerJar = Join-Path $root 'verification/aaaat-module-composition/caller/build/libs/independent-aaaat-caller.jar'
if (-not (Test-Path $dist)) { throw 'MADRE developer installation is missing' }
if ($null -eq $aaaatJar) { throw 'AAAAT Module artifact is missing' }
if (-not (Test-Path $callerJar)) { throw 'independent AAAAT caller artifact is missing' }
if (@(Get-ChildItem (Join-Path $dist 'lib') -Filter '*jackson*').Count -ne 0) {
    throw 'AAAAT implementation dependency leaked into madre-app distribution'
}
$jarEntries = (& jar tf $aaaatJar.FullName | Out-String)
if ($jarEntries -notmatch 'com/fasterxml/jackson/databind/ObjectMapper.class') {
    throw 'AAAAT Module artifact did not carry its own Jackson implementation dependency'
}

$launcher = if ($IsWindows) { Join-Path $dist 'bin/madre.bat' } else { Join-Path $dist 'bin/madre' }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('madre-aaaat-acceptance-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null
$oldXdgData = $env:XDG_DATA_HOME
$env:XDG_DATA_HOME = Join-Path $temp 'xdg-data'
$workspace = Join-Path $temp 'aaaat-workspace'
New-Item -ItemType Directory -Force -Path $workspace | Out-Null
$fake = Join-Path $temp 'fake-aaaat'
@'
#!/bin/sh
while IFS= read -r line; do
  case "$line" in
    *'"method":"initialize"'*)
      printf '%s\n' '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"AAAAT","version":"fixture"}}}'
      ;;
    *'"method":"tools/call"'*)
      case "$line" in
        *opportunity_research_context_read*)
          printf '%s\n' '{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"{\"title\":\"Senior Engineer\",\"company\":\"Deterministic Fixture\"}"}]}}'
          ;;
        *candidature_source_add*)
          printf '%s\n' '{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"{\"retained\":true}"}]}}'
          ;;
        *career_context_read*)
          printf '%s\n' '{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"{\"summary\":\"private fixture\"}"}]}}'
          ;;
        *)
          printf '%s\n' '{"jsonrpc":"2.0","id":2,"result":{"isError":true,"content":[{"type":"text","text":"unknown tool"}]}}'
          ;;
      esac
      ;;
  esac
done
'@ | Set-Content -Path $fake -Encoding utf8 -NoNewline
& chmod +x $fake
if ($LASTEXITCODE -ne 0) { throw 'failed to make deterministic AAAAT fixture executable' }

$config = Join-Path $temp 'madre.properties'
$state = Join-Path $temp 'state'
$db = Join-Path $temp 'kernel.sqlite'
@(
    "kernel.database=$(Slash $db)",
    "modules.state-directory=$(Slash $state)",
    'roles.core=io.github.didacll.madre.owner-interaction',
    'interaction.default-operation=fast-lane',
    'interaction.standard-operation=standard-prompt',
    'interaction.prompt-material-type=owner-prompt',
    'interaction.default-sensitivity=S5',
    'interaction.updates-operation=collect-background',
    'interaction.updates-material-type=background-collection-request',
    'interaction.updates-payload=collect',
    'interaction.updates-sensitivity=S1'
) | Set-Content -Path $config -Encoding utf8

function Invoke-Madre([string[]]$arguments) {
    $output = (& $launcher $config @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { Write-Host $output; throw "MADRE invocation failed with $LASTEXITCODE" }
    Write-Host $output
    return $output
}

function Invoke-MadreFailure([string[]]$arguments) {
    $output = (& $launcher $config @arguments 2>&1 | Out-String).Trim()
    $status = $LASTEXITCODE
    Write-Host $output
    if ($status -eq 0) { throw 'MADRE invocation unexpectedly succeeded' }
    return $output
}

try {
    $installed = Invoke-Madre @('modules', 'install', $aaaatJar.FullName)
    if ($installed -notmatch 'module\.installed\s+io\.github\.didacll\.madre\.aaaat') {
        throw 'AAAAT Module did not install through the normal owner lifecycle'
    }
    Invoke-Madre @('modules', 'configure', 'io.github.didacll.madre.aaaat',
        '--set', "executable-path=$(Slash $fake)", '--set', "workspace-path=$(Slash $workspace)") | Out-Null

    $callerInstalled = Invoke-Madre @('modules', 'install', $callerJar)
    if ($callerInstalled -notmatch 'module\.installed\s+verification\.aaaat-caller') {
        throw 'independent caller Module did not install through the normal owner lifecycle'
    }
    $listed = Invoke-Madre @('modules', 'list')
    if ($listed -notmatch 'io\.github\.didacll\.madre\.aaaat.*source=owner' -or
            $listed -notmatch 'verification\.aaaat-caller.*source=owner') {
        throw 'installed AAAAT experiment Modules were not owner-sourced artifacts'
    }

    $composed = Invoke-Madre @('--invoke-owner', 'verification.aaaat-caller',
        'probe', 'probe-request', 'S1', 'read-selected-opportunity')
    if ($composed -notmatch 'Senior Engineer' -or $composed -notmatch 'Deterministic Fixture') {
        throw 'independent Module-to-Module AAAAT composition did not return the domain result'
    }

    $public = Invoke-Madre @('--invoke-public', 'io.github.didacll.madre.aaaat',
        'opportunity-research-context-read', 'opportunity-research-request', 'S1', '{}')
    if ($public -notmatch 'available.*true') {
        throw 'AAAAT PUBLIC boundary did not use its explicit public transformation'
    }

    $privateFailure = Invoke-MadreFailure @('--invoke-owner', 'io.github.didacll.madre.aaaat',
        'career-context-read', 'career-context-request', 'S4', '{}')
    if ($privateFailure -notmatch 'not owner-callable|PUBLIC Operation is not installed') {
        throw 'non-exposed AAAAT implementation Operation became owner-callable'
    }
} finally {
    $env:XDG_DATA_HOME = $oldXdgData
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}
