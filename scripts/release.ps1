<#
  Publish a Lumen TV update.

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
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Use a version like 2.1.1" }
$current = ((Select-String -Path version.properties -Pattern '^versionName=(.+)$').Matches[0].Groups[1].Value).Trim()
if ([version]$Version -le [version]$current) { throw "$Version isn't newer than the current version ($current)." }

$escaped = [regex]::Escape($Version)
$notes = @(); $inSection = $false
foreach ($line in Get-Content CHANGELOG.md) {
    if ($line -match '^## ') { $inSection = $line -match "^## \[?v?$escaped(\]|\s|$)"; continue }
    if ($inSection) { $notes += $line }
}
if (-not ($notes -join '').Trim()) { throw "Add a '## $Version' section with notes to CHANGELOG.md first - it becomes the update notes in the app." }

(Get-Content version.properties) -replace '^versionName=.*', "versionName=$Version" | Set-Content version.properties -Encoding ascii
git add -A
git commit -m "Lumen TV $Version"
git tag "v$Version"

if ($Local) {
    cmd /c "build-apk.bat release < nul"
    $apk = "dist\LumenTV-$Version.apk"
    if (-not (Test-Path $apk)) { throw "Build didn't produce $apk" }
    git push origin HEAD
    git push origin "v$Version"
    $tmp = New-TemporaryFile; $notes | Set-Content $tmp -Encoding utf8
    gh release create "v$Version" $apk --title "Lumen TV $Version" --notes-file $tmp
    Remove-Item $tmp
    Write-Host "Published Lumen TV $Version." -ForegroundColor Green
} else {
    git push origin HEAD
    git push origin "v$Version"
    Write-Host "Pushed v$Version. GitHub Actions is building and publishing it (Actions tab, ~5 minutes)." -ForegroundColor Green
}
