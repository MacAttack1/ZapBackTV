<#
  Publish a ZapBackTV update.

      powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Version 2.1.1

  1. Write the notes first: add a "## 2.1.1" section at the top of CHANGELOG.md.
     Those notes are what the app shows in its update dialog.
  2. This script sets the version, commits, tags v2.1.1 and pushes. GitHub Actions then builds the
     signed APK and publishes the release; every installed copy offers the update on its next check.

  -Local   Build the APK on this PC and upload it yourself instead of using GitHub Actions.
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [switch]$Local
)
# Exit codes are checked by hand: git and gh print normal progress on stderr, which Windows
# PowerShell 5 would otherwise treat as fatal errors.
$ErrorActionPreference = 'Continue'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Fail($msg) { Write-Host ""; Write-Host "  [!] $msg" -ForegroundColor Red; Write-Host ""; exit 1 }
function Run { param([scriptblock]$cmd, [string]$what) & $cmd; if ($LASTEXITCODE -ne 0) { Fail "$what failed - see the message above." } }

if ($Version -notmatch '^\d+\.\d+\.\d+$') { Fail "Use a version like 2.1.1" }
$current = ((Select-String -Path version.properties -Pattern '^versionName=(.+)$').Matches[0].Groups[1].Value).Trim()
if ([version]$Version -le [version]$current) { Fail "$Version isn't newer than the current version ($current)." }

$escaped = [regex]::Escape($Version)
$notes = @(); $inSection = $false
foreach ($line in Get-Content CHANGELOG.md) {
    if ($line -match '^## ') { $inSection = $line -match "^## \[?v?$escaped(\]|\s|$)"; continue }
    if ($inSection) { $notes += $line }
}
if (-not ($notes -join '').Trim()) { Fail "Add a '## $Version' section with notes to CHANGELOG.md first - it becomes the update notes in the app." }

(Get-Content version.properties) -replace '^versionName=.*', "versionName=$Version" | Set-Content version.properties -Encoding ascii
Run { git add -A } 'git add'
Run { git commit -m "ZapBackTV $Version" } 'Commit'
Run { git tag "v$Version" } 'Tagging'

if ($Local) {
    cmd /c "build-apk.bat release < nul"
    $apk = "dist\ZapBackTV-$Version.apk"
    if (-not (Test-Path $apk)) { Fail "Build didn't produce $apk" }
    Run { git push origin HEAD } 'Push'
    Run { git push origin "v$Version" } 'Pushing the tag'
    $tmp = New-TemporaryFile; $notes | Set-Content $tmp -Encoding utf8
    Run { gh release create "v$Version" $apk --title "ZapBackTV $Version" --notes-file $tmp } 'Creating the release'
    Remove-Item $tmp
    Write-Host "Published ZapBackTV $Version." -ForegroundColor Green
} else {
    Run { git push origin HEAD } 'Push'
    Run { git push origin "v$Version" } 'Pushing the tag'
    Write-Host "Pushed v$Version. GitHub Actions is building and publishing it (Actions tab, ~5 minutes)." -ForegroundColor Green
}
