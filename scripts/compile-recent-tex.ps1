[CmdletBinding()]
param(
    [string]$RepositoryRoot,
    [switch]$All,
    [int]$RecentDays = 7,
    [int]$Passes = 2
)

$ErrorActionPreference = "Stop"

if (-not $RepositoryRoot) {
    $RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
    $RepositoryRoot = (Resolve-Path $RepositoryRoot).Path
}

$texRoot = Join-Path $RepositoryRoot "docs\tex"
$auxDir = Join-Path $texRoot "auxfiles"
$pdfDir = Join-Path $RepositoryRoot "docs"

if (-not (Test-Path $texRoot)) {
    throw "TeX root not found: $texRoot"
}

$rootDocuments = @(Get-ChildItem -Path $texRoot -Filter "*.tex" -File)
if ($rootDocuments.Count -eq 0) {
    throw "No root TeX documents found under $texRoot"
}

if ($All) {
    $documentsToCompile = $rootDocuments
} else {
    $cutoff = (Get-Date).AddDays(-1 * $RecentDays)
    $recentTex = @(
        Get-ChildItem -Path $texRoot -Recurse -Filter "*.tex" -File |
            Where-Object {
                $_.FullName -notlike (Join-Path $auxDir "*") -and
                $_.LastWriteTime -ge $cutoff
            }
    )

    if ($recentTex.Count -eq 0) {
        Write-Host "No TeX files modified in the last $RecentDays days under $texRoot."
        exit 0
    }

    $nestedOrSharedChanged = $recentTex | Where-Object { $_.DirectoryName -ne $texRoot }
    if ($nestedOrSharedChanged.Count -gt 0) {
        $documentsToCompile = $rootDocuments
    } else {
        $documentsToCompile = $recentTex
    }
}

New-Item -ItemType Directory -Force -Path $auxDir | Out-Null
New-Item -ItemType Directory -Force -Path $pdfDir | Out-Null

Push-Location $texRoot
try {
    foreach ($document in @($documentsToCompile | Sort-Object FullName -Unique)) {
        Write-Host "Compiling $($document.Name)"
        for ($pass = 1; $pass -le $Passes; $pass++) {
            & pdflatex `
                -interaction=nonstopmode `
                -halt-on-error `
                -file-line-error `
                "-aux-directory=$auxDir" `
                "-output-directory=$pdfDir" `
                $document.Name

            if ($LASTEXITCODE -ne 0) {
                throw "pdflatex failed for $($document.Name) on pass $pass"
            }
        }
    }
} finally {
    Pop-Location
}

Get-ChildItem -Path $auxDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Write-Host "Compiled PDFs are in $pdfDir"
Write-Host "Auxiliary files removed from $auxDir"
