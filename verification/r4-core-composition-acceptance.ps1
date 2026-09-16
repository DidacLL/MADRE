param(
    [Parameter(Mandatory = $true)][string]$AppImageRoot,
    [Parameter(Mandatory = $true)][string]$ModuleJar,
    [Parameter(Mandatory = $true)][string]$ReplacementModuleJar
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Get-Location).Path)
$separator = [IO.Path]::DirectorySeparatorChar.ToString()
$rootPrefix = if ($root.EndsWith($separator)) { $root } else { $root + $separator }

function Resolve-ExternalArtifact([string]$path, [string]$label) {
    if (-not (Test-Path -LiteralPath $path)) { throw "$label is missing: $path" }
    $full = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $path).Path)
    if ($full.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or
            $full.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$label must be built outside the MADRE checkout: $full"
    }
    return $full
}

$appImage = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $AppImageRoot).Path)
$initialModule = Resolve-ExternalArtifact $ModuleJar 'R4 proving Module'
$replacementModule = Resolve-ExternalArtifact $ReplacementModuleJar 'R4 replacement Module'
if ((Get-FileHash -LiteralPath $initialModule -Algorithm SHA256).Hash -eq
        (Get-FileHash -LiteralPath $replacementModule -Algorithm SHA256).Hash) {
    throw 'R4 initial and replacement Module artifacts are byte-identical'
}
$launcher = if ($IsWindows) { Join-Path $appImage 'madre.exe' } else { Join-Path $appImage 'bin/madre' }
if (-not (Test-Path $launcher)) { throw "packaged launcher is missing: $launcher" }

$temp = Join-Path ([IO.Path]::GetTempPath()) ('madre-r4-composition-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null
$selectionLog = Join-Path $temp 'selection.log'
$interpretationLog = Join-Path $temp 'interpretation.log'
$privateLeak = Join-Path $temp 'private-leak.log'
$oldHome = $env:HOME
$oldAppData = $env:APPDATA
$oldLocalAppData = $env:LOCALAPPDATA
$oldXdgConfig = $env:XDG_CONFIG_HOME
$oldXdgData = $env:XDG_DATA_HOME
$oldXdgState = $env:XDG_STATE_HOME

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
$listener.Stop()
$prefix = "http://127.0.0.1:$port/"

$server = Start-Job -ArgumentList $prefix, $selectionLog, $interpretationLog, $privateLeak -ScriptBlock {
    param($prefix, $selectionLog, $interpretationLog, $privateLeak)
    $ErrorActionPreference = 'Stop'
    $http = [Net.HttpListener]::new()
    $http.Prefixes.Add($prefix)
    $http.Start()

    function Candidate-Number([string]$prompt, [string]$purpose) {
        $escaped = [regex]::Escape($purpose)
        $match = [regex]::Match($prompt,
            "(?m)^(\d+)\. [^\r\n]*operationPurpose=$escaped;")
        if ($match.Success) { return $match.Groups[1].Value }
        return $null
    }

    try {
        while ($true) {
            $context = $http.GetContext()
            try {
                $request = $context.Request
                $response = $context.Response
                $response.ContentType = 'application/json'
                if ($request.HttpMethod -eq 'GET' -and $request.Url.AbsolutePath -eq '/v1/models') {
                    $body = '{"data":[{"id":"r4-coordination"}]}'
                } elseif ($request.HttpMethod -eq 'POST' -and
                        $request.Url.AbsolutePath -eq '/v1/chat/completions') {
                    $reader = [IO.StreamReader]::new($request.InputStream, $request.ContentEncoding)
                    try { $payload = ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
                    $prompt = [string]$payload.messages[0].content
                    if ($prompt.StartsWith('Decide whether one currently reachable installed Module Operation')) {
                        Add-Content -Path $selectionLog -Value ("---SELECTION---`n" + $prompt) -Encoding utf8
                        $objectiveMatch = [regex]::Match($prompt,
                            'OWNER OBJECTIVE:\s*(.*?)\s*REACHABLE OPERATIONS:',
                            [Text.RegularExpressions.RegexOptions]::Singleline)
                        $objective = if ($objectiveMatch.Success) {
                            $objectiveMatch.Groups[1].Value.Trim().ToLowerInvariant()
                        } else { '' }
                        $choice = $null
                        if ($objective.Contains('private') -and $objective.Contains('audit')) {
                            $choice = Candidate-Number $prompt 'Read a highly sensitive workspace audit value owned by this Module'
                        } elseif ($objective.Contains('record') -or $objective.Contains('save') -or
                                $objective.Contains('add')) {
                            $choice = Candidate-Number $prompt 'Store one owner-provided project or workspace note in Module-owned local state'
                        } elseif (($objective.Contains('what') -or $objective.Contains('summar') -or
                                $objective.Contains('list') -or $objective.Contains('read')) -and
                                $objective.Contains('note')) {
                            $choice = Candidate-Number $prompt 'Read project or workspace notes stored by this Module and return a concise state summary'
                        }
                        $text = if ([string]::IsNullOrWhiteSpace($choice)) { 'NONE' } else { "USE $choice" }
                    } elseif ($prompt.StartsWith('Interpret the installed Module result below')) {
                        Add-Content -Path $interpretationLog -Value ("---INTERPRETATION---`n" + $prompt) -Encoding utf8
                        if ($prompt.Contains('private-audit=workspace-owner-only')) {
                            Set-Content -Path $privateLeak -Value $prompt -Encoding utf8
                            $text = 'UNSAFE PRIVATE RESULT'
                        } elseif ($prompt -match 'payload=behavior=([^;]+); action=recorded; count=([0-9]+)') {
                            $text = "Workspace note saved through the installed Module ($($Matches[1]))."
                        } elseif ($prompt -match 'payload=behavior=([^;]+); action=summary; count=([0-9]+)') {
                            $text = "The installed Module ($($Matches[1])) reports $($Matches[2]) saved workspace note(s)."
                        } else {
                            $text = 'The installed Module returned a usable result.'
                        }
                    } elseif ($prompt.StartsWith("Analyze the owner's bounded conversation below")) {
                        $text = 'NO_FOLLOW_UP'
                    } elseif ($prompt -match '(?i)(workspace|project).*(note|audit)' -or
                            $prompt -match '(?i)(note|audit).*(workspace|project)') {
                        $text = 'No compatible installed Module capability is currently available for that request.'
                    } else {
                        $text = 'Ordinary CORE fallback response.'
                    }
                    $body = @{ choices = @(@{ message = @{ content = $text }; finish_reason = 'stop' });
                        usage = @{ prompt_tokens = 1; completion_tokens = 1 } } |
                            ConvertTo-Json -Depth 6 -Compress
                } else {
                    $response.StatusCode = 404
                    $body = '{"error":"not found"}'
                }
                $bytes = [Text.Encoding]::UTF8.GetBytes($body)
                $response.ContentLength64 = $bytes.Length
                $response.OutputStream.Write($bytes, 0, $bytes.Length)
            } catch {
                try { $context.Response.StatusCode = 500 } catch { }
            } finally {
                try { $context.Response.Close() } catch { }
            }
        }
    } finally {
        $http.Stop()
        $http.Close()
    }
}

function Invoke-Madre([string[]]$arguments) {
    $output = (& $launcher @arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { Write-Host $output; throw "MADRE failed with $LASTEXITCODE" }
    Write-Host $output
    return $output
}

function Invoke-Conversation([string]$line) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $launcher
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw 'could not start packaged MADRE console' }
    Start-Sleep -Milliseconds 700
    $process.StandardInput.WriteLine($line)
    Start-Sleep -Milliseconds 1200
    $process.StandardInput.WriteLine('/exit')
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(20000)) {
        $process.Kill($true)
        throw 'packaged MADRE console did not exit'
    }
    $output = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
    Write-Host $output
    if ($process.ExitCode -ne 0) { throw "packaged console failed with $($process.ExitCode)" }
    return $output
}

function Workspace-State([string]$path) {
    if (-not (Test-Path $path)) { return @() }
    return @(Get-Content $path | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_)) })
}

try {
    $env:HOME = Join-Path $temp 'home'
    if ($IsWindows) {
        $env:APPDATA = Join-Path $temp 'roaming'
        $env:LOCALAPPDATA = Join-Path $temp 'local'
        $stateDirectory = Join-Path $env:LOCALAPPDATA 'MADRE/state'
    } else {
        $env:XDG_CONFIG_HOME = Join-Path $temp 'xdg-config'
        $env:XDG_DATA_HOME = Join-Path $temp 'xdg-data'
        $env:XDG_STATE_HOME = Join-Path $temp 'xdg-state'
        $stateDirectory = Join-Path $env:XDG_STATE_HOME 'madre'
    }
    New-Item -ItemType Directory -Force -Path $env:HOME | Out-Null
    $workspaceState = Join-Path $stateDirectory 'module-state/verification-workspace.state'

    Invoke-Madre @('doctor') | Out-Null
    Invoke-Madre @('reasoning', 'configure', 'openai-compatible', 'r4',
        '--set', 'capability-id=r4-coordination', '--set', "endpoint=$($prefix)v1/",
        '--set', 'model=r4-coordination', '--set', 'privacy=SECRET', '--set', 'location=LOCAL',
        '--set', 'preference=1000') | Out-Null

    $absent = Invoke-Conversation 'Please record this project workspace note: absent-module-check'
    if ($absent -notmatch 'No compatible installed Module capability') {
        throw 'owner did not receive a useful ordinary-conversation failure while the Module was absent'
    }
    if (Test-Path $workspaceState) { throw 'absent Module unexpectedly created application state' }

    $installed = Invoke-Madre @('modules', 'install', $initialModule)
    if ($installed -notmatch 'module\.installed\s+verification\.workspace' -or
            $installed -notmatch 'source=owner') {
        throw 'R4 proving Module was not installed through the ordinary owner lifecycle'
    }
    $listed = Invoke-Madre @('modules', 'list')
    if ($listed -notmatch 'module\s+verification\.workspace.*source=owner') {
        throw 'R4 proving Module is not visible as an independently installed owner artifact'
    }

    $recordRequest = 'Please record this project workspace note: R4 composition reached the external Module'
    $record = Invoke-Conversation $recordRequest
    if ($record -notmatch 'Workspace note saved through the installed Module \(v1\)') {
        throw 'ordinary owner conversation did not execute the independently installed write Operation'
    }
    foreach ($internal in @('verification.workspace', 'workspace-result', 'record-note',
            'causal=I2', 'sensitivity=S2', 'MaterialId')) {
        if ($record -match [regex]::Escape($internal)) {
            throw "owner-visible result leaked internal Module protocol: $internal"
        }
    }
    $notes = @(Workspace-State $workspaceState)
    if ($notes.Count -ne 1 -or $notes[0] -ne $recordRequest) {
        throw 'target Module-owned write state was not created by the ordinary conversational path'
    }

    if (-not (Test-Path $interpretationLog)) {
        throw 'CORE never interpreted returned foreign Material'
    }
    $evidence = Get-Content $interpretationLog -Raw
    foreach ($required in @('producerModule=verification.workspace',
            'materialType=verification.workspace/workspace-result', 'sensitivity=S2',
            'payload=behavior=v1; action=recorded; count=1', 'causal=I2')) {
        if ($evidence -notmatch [regex]::Escape($required)) {
            throw "foreign Material evidence is missing: $required"
        }
    }
    if ($evidence -notmatch 'materialId=[0-9a-fA-F-]{20,}') {
        throw 'foreign target-owned Material identity was not preserved into CORE interpretation'
    }

    $read = Invoke-Conversation 'What project workspace notes are stored?'
    if ($read -notmatch 'installed Module \(v1\) reports 1 saved workspace note') {
        throw 'later ordinary owner request did not read target Module-owned state'
    }

    if (-not (Test-Path $selectionLog)) { throw 'CORE semantic selection evidence is missing' }
    $selectionEvidence = Get-Content $selectionLog -Raw
    foreach ($purpose in @(
            'Store one owner-provided project or workspace note in Module-owned local state',
            'Read project or workspace notes stored by this Module and return a concise state summary',
            'Read a highly sensitive workspace audit value owned by this Module')) {
        if ($selectionEvidence -notmatch [regex]::Escape($purpose)) {
            throw "exposed Operation was absent from caller-bound discovery: $purpose"
        }
    }
    if ($selectionEvidence -match [regex]::Escape('Delete every workspace note stored by this Module')) {
        throw 'unexposed reset Operation appeared in ordinary Module discovery'
    }

    $beforeReset = @(Workspace-State $workspaceState)
    $reset = Invoke-Conversation 'Delete every workspace note stored by the workspace application'
    if ($reset -notmatch 'No compatible installed Module capability') {
        throw 'unexposed capability did not fail through an owner-facing ordinary response'
    }
    $afterReset = @(Workspace-State $workspaceState)
    if (($beforeReset -join "`n") -ne ($afterReset -join "`n")) {
        throw 'unexposed reset Operation changed target state'
    }

    $private = Invoke-Conversation 'Show the private workspace audit value'
    if ($private -notmatch 'found an installed Module.*could not be used safely') {
        throw 'too-sensitive foreign Material did not fail safely at the Module receiver boundary'
    }
    if (Test-Path $privateLeak) {
        throw 'S5 foreign Material crossed the Module receiver boundary into CORE interpretation'
    }
    $postPrivateEvidence = Get-Content $interpretationLog -Raw
    if ($postPrivateEvidence -match 'private-audit=workspace-owner-only') {
        throw 'private audit payload appeared in CORE interpretation evidence'
    }

    $replaced = Invoke-Madre @('modules', 'install', $replacementModule, '--replace')
    if ($replaced -notmatch 'module\.replaced\s+verification\.workspace' -or
            $replaced -notmatch 'source=owner') {
        throw 'R4 proving Module replacement did not use the ordinary owner lifecycle'
    }
    $replacementRead = Invoke-Conversation 'What project workspace notes are stored?'
    if ($replacementRead -notmatch 'installed Module \(v2\) reports 1 saved workspace note') {
        throw 'CORE did not observe independently replaced Module behavior without a product rebuild'
    }
    $replacementState = @(Workspace-State $workspaceState)
    if ($replacementState.Count -ne 1 -or $replacementState[0] -ne $recordRequest) {
        throw 'Module-owned state did not remain available after independent artifact replacement'
    }

    $uninstalled = Invoke-Madre @('modules', 'uninstall', 'verification.workspace')
    if ($uninstalled -notmatch 'module\.uninstalled\s+verification\.workspace') {
        throw 'R4 proving Module was not uninstalled through the ordinary owner lifecycle'
    }
    $afterUninstall = Invoke-Conversation 'What project workspace notes are stored?'
    if ($afterUninstall -notmatch 'No compatible installed Module capability') {
        throw 'owner did not receive a useful ordinary-conversation failure after Module uninstall'
    }
} finally {
    Stop-Job $server -ErrorAction SilentlyContinue | Out-Null
    Remove-Job $server -Force -ErrorAction SilentlyContinue
    $env:HOME = $oldHome
    $env:APPDATA = $oldAppData
    $env:LOCALAPPDATA = $oldLocalAppData
    $env:XDG_CONFIG_HOME = $oldXdgConfig
    $env:XDG_DATA_HOME = $oldXdgData
    $env:XDG_STATE_HOME = $oldXdgState
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}
