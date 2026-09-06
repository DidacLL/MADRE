# Optional Windows CORE product-acceptance fixture. Never run during ordinary package bootstrap.
param([string]$Destination = (Join-Path $env:LOCALAPPDATA 'MADRE\inference'))
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Destination = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null

function Get-VerifiedArtifact([string]$Url, [string]$Path, [string]$Sha256) {
    if (-not (Test-Path -LiteralPath $Path)) {
        Invoke-WebRequest -Uri $Url -OutFile $Path
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $Sha256) { throw "Checksum mismatch: $Path. Remove that artifact and retry." }
}

$archive = Join-Path $Destination 'llama-b10809-bin-win-cpu-x64.zip'
Get-VerifiedArtifact `
    'https://github.com/ggml-org/llama.cpp/releases/download/b10809/llama-b10809-bin-win-cpu-x64.zip' `
    $archive '9df3158ed228a641a4b127942d7f459f24c9e13f04682659d05c00c80099b6b5'
$serverDir = Join-Path $Destination 'llama-b10809'
if (-not (Test-Path -LiteralPath $serverDir)) {
    Expand-Archive -LiteralPath $archive -DestinationPath $serverDir
}

$model = Join-Path $Destination 'qwen2.5-1.5b-instruct-q4_k_m.gguf'
Get-VerifiedArtifact `
    'https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/a615a81362316d7b9f5a7a9c4313adfdf9b54588/qwen2.5-1.5b-instruct-q4_k_m.gguf' `
    $model '6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e'
Get-ChildItem -LiteralPath $serverDir -Filter llama-server.exe -Recurse | Select-Object -ExpandProperty FullName
Write-Output $model
