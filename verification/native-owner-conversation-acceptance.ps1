param(
    [Parameter(Mandatory = $true)][string]$AppImageRoot
)

$ErrorActionPreference = 'Stop'
$appImage = (Resolve-Path $AppImageRoot).Path
$launcher = if ($IsWindows) { Join-Path $appImage 'madre.exe' } else { Join-Path $appImage 'bin/madre' }
if (-not (Test-Path $launcher)) { throw "packaged launcher is missing: $launcher" }

$temp = Join-Path ([IO.Path]::GetTempPath()) ('madre-owner-conversation-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $temp | Out-Null
$gate = Join-Path $temp 'background.gate'
$leak = Join-Path $temp 'raw-sensitive-leak.txt'
$rawSensitive = 's5-owner-value-7391'
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

$server = Start-Job -ArgumentList $prefix, $gate, $leak, $rawSensitive -ScriptBlock {
    param($prefix, $gate, $leak, $rawSensitive)
    $ErrorActionPreference = 'Stop'
    $http = [Net.HttpListener]::new()
    $http.Prefixes.Add($prefix)
    $http.Start()
    try {
        while ($true) {
            $context = $http.GetContext()
            try {
                $request = $context.Request
                $response = $context.Response
                $response.ContentType = 'application/json'
                if ($request.HttpMethod -eq 'GET' -and $request.Url.AbsolutePath -eq '/v1/models') {
                    $body = '{"data":[{"id":"owner-acceptance"}]}'
                } elseif ($request.HttpMethod -eq 'POST' -and
                        $request.Url.AbsolutePath -eq '/v1/chat/completions') {
                    $reader = [IO.StreamReader]::new($request.InputStream, $request.ContentEncoding)
                    try { $payload = ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
                    $prompt = [string]$payload.messages[0].content
                    $model = [string]$payload.model
                    if ($prompt.Contains($rawSensitive)) {
                        Set-Content -Path $leak -Value $prompt -Encoding utf8
                        $text = 'RAW-SENSITIVE-LEAK'
                    } elseif ($prompt.StartsWith("Analyze the owner's bounded conversation below")) {
                        if ($prompt -match 'CURRENT OWNER:\s*start durable analysis') {
                            while (-not (Test-Path $gate)) { Start-Sleep -Milliseconds 50 }
                            $text = 'durable-follow-up'
                        } else {
                            $text = 'NO_FOLLOW_UP'
                        }
                    } elseif ($prompt -match 'preferred name: Grace' -and
                            $prompt -match 'workspace: /srv/madre') {
                        $text = "$model`:knowledge-recovered"
                    } elseif ($prompt -match 'preferred name: Ada' -and
                            $prompt -match 'interaction style: concise replies' -and
                            $prompt -match 'workspace: /srv/madre') {
                        $text = "$model`:knowledge-grounded"
                    } elseif ($prompt -match 'access code: a highly sensitive owner value is stored inside CORE' -and
                            $prompt -notmatch [regex]::Escape($rawSensitive)) {
                        $text = "$model`:opaque-mediated"
                    } elseif ($prompt -match 'OWNER TURN 1:' -and $prompt -match 'remember alpha') {
                        $text = "$model`:context-preserved"
                    } else {
                        $text = "$model`:first-answer"
                    }
                    $body = @{ choices = @(@{ message = @{ content = $text }; finish_reason = 'stop' });
                        usage = @{ prompt_tokens = 1; completion_tokens = 1 } } | ConvertTo-Json -Depth 6 -Compress
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

function Invoke-Console([scriptblock]$drive) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $launcher
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw 'could not start packaged MADRE console' }
    & $drive $process
    $process.StandardInput.WriteLine('/exit')
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(15000)) { $process.Kill($true); throw 'packaged console did not exit' }
    $output = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
    Write-Host $output
    if ($process.ExitCode -ne 0) { throw "packaged console failed with $($process.ExitCode)" }
    return $output
}

try {
    $env:HOME = Join-Path $temp 'home'
    if ($IsWindows) {
        $env:APPDATA = Join-Path $temp 'roaming'
        $env:LOCALAPPDATA = Join-Path $temp 'local'
        $configuration = Join-Path $env:APPDATA 'MADRE/madre.properties'
        $stateDirectory = Join-Path $env:LOCALAPPDATA 'MADRE/state'
    } else {
        $env:XDG_CONFIG_HOME = Join-Path $temp 'xdg-config'
        $env:XDG_DATA_HOME = Join-Path $temp 'xdg-data'
        $env:XDG_STATE_HOME = Join-Path $temp 'xdg-state'
        $configuration = Join-Path $env:XDG_CONFIG_HOME 'madre/madre.properties'
        $stateDirectory = Join-Path $env:XDG_STATE_HOME 'madre'
    }
    New-Item -ItemType Directory -Force -Path $env:HOME | Out-Null

    Invoke-Madre @('doctor') | Out-Null
    $propertiesText = Get-Content $configuration -Raw
    if ($propertiesText -match '(?m)^interaction\.') {
        throw 'first-run owner configuration still contains host semantic interaction protocol keys'
    }
    $providers = Invoke-Madre @('reasoning', 'providers')
    if ($providers -notmatch 'openai-compatible') { throw 'shipped OpenAI-compatible provider is missing' }

    Invoke-Madre @('reasoning', 'configure', 'openai-compatible', 'public-fast',
        '--set', 'capability-id=owner-public-fast', '--set', "endpoint=$($prefix)v1/",
        '--set', 'model=public-model', '--set', 'privacy=PUBLIC', '--set', 'location=LOCAL',
        '--set', 'preference=1000') | Out-Null
    Invoke-Madre @('reasoning', 'configure', 'openai-compatible', 'local',
        '--set', 'capability-id=owner-local', '--set', "endpoint=$($prefix)v1/",
        '--set', 'model=local-model', '--set', 'privacy=LOCAL', '--set', 'location=LOCAL',
        '--set', 'preference=500') | Out-Null
    Invoke-Madre @('reasoning', 'configure', 'openai-compatible', 'secret',
        '--set', 'capability-id=owner-secret', '--set', "endpoint=$($prefix)v1/",
        '--set', 'model=secret-model', '--set', 'privacy=SECRET', '--set', 'location=LOCAL',
        '--set', 'preference=1') | Out-Null

    $configured = Invoke-Madre @('doctor')
    if ($configured -notmatch 'reasoning\.mechanisms\.count\s+3') {
        throw 'configured real provider instances did not materialize'
    }

    $first = Invoke-Console {
        param($process)
        Start-Sleep -Milliseconds 700
        $process.StandardInput.WriteLine('/sensitivity S1')
        $process.StandardInput.WriteLine('remember alpha')
        Start-Sleep -Milliseconds 600
        $process.StandardInput.WriteLine('what did I ask you to remember?')
        Start-Sleep -Milliseconds 600
        $process.StandardInput.WriteLine('Remember my preferred name is Ada')
        $process.StandardInput.WriteLine('I prefer concise replies')
        $process.StandardInput.WriteLine('Remember my workspace is /srv/madre')
        $process.StandardInput.WriteLine("Remember my access code is $rawSensitive")
        Start-Sleep -Milliseconds 500
        $process.StandardInput.WriteLine('Use my preferred name and workspace when you answer')
        Start-Sleep -Milliseconds 700
        $process.StandardInput.WriteLine('Help me use my access code without revealing it')
        Start-Sleep -Milliseconds 700
        $process.StandardInput.WriteLine('Show me my access code')
        $process.StandardInput.WriteLine('Remember my preferred name is Grace')
        Start-Sleep -Milliseconds 500
        $process.StandardInput.WriteLine('/sensitivity S5')
        $process.StandardInput.WriteLine('sensitive mechanism check')
        Start-Sleep -Milliseconds 700
        $process.StandardInput.WriteLine('/sensitivity S1')
        $process.StandardInput.WriteLine('start durable analysis')
        Start-Sleep -Milliseconds 600
    }
    if ($first -notmatch 'public-model:first-answer') {
        throw 'low-sensitivity ordinary owner turn did not use the PUBLIC-compatible mechanism'
    }
    if ($first -notmatch 'public-model:context-preserved') {
        throw 'ordinary multi-turn conversation did not reuse Agent-owned persisted context'
    }
    if ($first -notmatch 'local-model:knowledge-grounded') {
        throw 'classified owner/environment knowledge did not participate through a compatible mechanism'
    }
    if ($first -notmatch 'local-model:opaque-mediated') {
        throw 'highly sensitive knowledge did not use the opaque reasoning mediation path'
    }
    if ($first -notmatch [regex]::Escape($rawSensitive)) {
        throw 'controlled owner-visible resolution did not return the stored sensitive value'
    }
    if ($first -notmatch 'secret-model:first-answer') {
        throw 'S5 ordinary context did not select the SECRET-compatible mechanism'
    }
    if (Test-Path $leak) {
        throw 'raw highly sensitive knowledge reached a reasoning mechanism'
    }

    $moduleState = Join-Path $stateDirectory 'module-state'
    $pending = Join-Path $moduleState 'owner-interaction-background.state'
    $knowledgeState = Join-Path $moduleState 'owner-interaction-background.state.knowledge'
    if (-not (Test-Path $pending) -or (Get-Item $pending).Length -eq 0) {
        throw 'Module pending durable association was not persisted before restart'
    }
    if (-not (Test-Path $knowledgeState) -or (Get-Item $knowledgeState).Length -eq 0) {
        throw 'CORE semantic knowledge was not persisted in Module-owned state before restart'
    }
    if (-not (Test-Path (Join-Path $stateDirectory 'kernel-work.sqlite'))) {
        throw 'Kernel durable state was not persisted independently before restart'
    }

    Set-Content -Path $gate -Value 'open' -Encoding utf8
    $second = Invoke-Console {
        param($process)
        # Graceful shutdown interrupted the in-flight attempt. The durable request is re-queued by
        # its existing retry policy with a 5-second delay, so keep the restarted host alive past
        # that eligibility time and let presentation polling surface the Agent-approved result.
        Start-Sleep -Seconds 8
        $process.StandardInput.WriteLine('/sensitivity S1')
        $process.StandardInput.WriteLine('What is my preferred name?')
        Start-Sleep -Milliseconds 300
        $process.StandardInput.WriteLine('Use my preferred name and workspace when you answer')
        Start-Sleep -Milliseconds 900
    }
    if ($second -notmatch 'follow-up\s+durable-follow-up') {
        throw 'CORE Agent-approved recovered durable work was not surfaced naturally after restart'
    }
    if ($second -notmatch 'preferred name is Grace') {
        throw 'edited owner knowledge was not recovered through ordinary owner interaction'
    }
    if ($second -notmatch 'secret-model:knowledge-recovered') {
        throw 'conversation history and recovered knowledge did not combine conservatively after restart'
    }
    if (Test-Path $leak) {
        throw 'raw highly sensitive knowledge reached a reasoning mechanism after restart'
    }
    if ((Test-Path $pending) -and (Get-Item $pending).Length -ne 0) {
        throw 'Module pending association was not acknowledged after semantic follow-up collection'
    }

    $normalOutput = $first + [Environment]::NewLine + $second
    $privateProtocol = @('fast-lane', 'standard-prompt', 'collect-background', 'owner-prompt',
        'background-collection-request', 'change-owner-knowledge', 'read-owner-knowledge',
        'knowledge-command', 'owner-knowledge', 'knowledge-reference',
        'NO_FOLLOW_UP', '/standard', '/updates')
    foreach ($token in $privateProtocol) {
        if ($normalOutput -match [regex]::Escape($token)) {
            throw "ordinary owner output exposed CORE implementation protocol: $token"
        }
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
