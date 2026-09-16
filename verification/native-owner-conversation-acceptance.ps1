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

$server = Start-Job -ArgumentList $prefix, $gate -ScriptBlock {
    param($prefix, $gate)
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
                    if ($prompt.StartsWith("Analyze the owner's bounded conversation below")) {
                        if ($prompt -match 'CURRENT OWNER:\s*start durable analysis') {
                            while (-not (Test-Path $gate)) { Start-Sleep -Milliseconds 50 }
                            $text = 'durable-follow-up'
                        } else {
                            $text = 'NO_FOLLOW_UP'
                        }
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
    Invoke-Madre @('reasoning', 'configure', 'openai-compatible', 'secret',
        '--set', 'capability-id=owner-secret', '--set', "endpoint=$($prefix)v1/",
        '--set', 'model=secret-model', '--set', 'privacy=SECRET', '--set', 'location=LOCAL',
        '--set', 'preference=1') | Out-Null

    $configured = Invoke-Madre @('doctor')
    if ($configured -notmatch 'reasoning\.mechanisms\.count\s+2') {
        throw 'configured real provider instances did not materialize'
    }

    $first = Invoke-Console {
        param($process)
        Start-Sleep -Milliseconds 700
        $process.StandardInput.WriteLine('remember alpha')
        Start-Sleep -Milliseconds 600
        $process.StandardInput.WriteLine('/sensitivity S1')
        $process.StandardInput.WriteLine('what did I ask you to remember?')
        Start-Sleep -Milliseconds 600
        $process.StandardInput.WriteLine('start durable analysis')
        Start-Sleep -Milliseconds 600
    }
    if ($first -notmatch 'secret-model:first-answer') {
        throw 'ordinary owner turn did not select the SECRET-compatible mechanism'
    }
    if ($first -notmatch 'secret-model:context-preserved') {
        throw 'ordinary multi-turn conversation did not reuse Agent-owned persisted context'
    }

    $moduleState = Join-Path $stateDirectory 'module-state'
    $pending = Join-Path $moduleState 'owner-interaction-background.state'
    if (-not (Test-Path $pending) -or (Get-Item $pending).Length -eq 0) {
        throw 'Module pending durable association was not persisted before restart'
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
        $process.StandardInput.WriteLine('after restart')
        Start-Sleep -Milliseconds 900
    }
    if ($second -notmatch 'follow-up\s+durable-follow-up') {
        throw 'CORE Agent-approved recovered durable work was not surfaced naturally after restart'
    }
    if ($second -notmatch 'context-preserved') {
        throw 'Module-owned conversation state was not recovered for an ordinary turn after restart'
    }
    if ((Test-Path $pending) -and (Get-Item $pending).Length -ne 0) {
        throw 'Module pending association was not acknowledged after semantic follow-up collection'
    }

    $normalOutput = $first + [Environment]::NewLine + $second
    $privateProtocol = @('fast-lane', 'standard-prompt', 'collect-background', 'owner-prompt',
        'background-collection-request', 'NO_FOLLOW_UP', '/standard', '/updates')
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
