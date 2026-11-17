<#
  Renames your existing GitHub repository to ZapBackTV and points this project at it.

      powershell -ExecutionPolicy Bypass -File scripts\rename-repo.ps1

  GitHub keeps redirecting the old name, so nothing already pushed (or installed) breaks.
  Afterwards: rebuild, then publish with scripts\release.ps1 so the app checks the new name.
#>
$ErrorActionPreference = 'Continue'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Fail($msg) { Write-Host ""; Write-Host "  [!] $msg" -ForegroundColor Red; Write-Host ""; exit 1 }
$newName = 'ZapBackTV'

# The git remote is the truth about where the code currently lives.
$url = (git remote get-url origin 2>$null)
if (-not $url) { Fail "This folder has no 'origin' remote. Run scripts\github-setup.ps1 instead." }
if ($url -notmatch 'github\.com[:/]([^/]+)/([^/]+?)(\.git)?$') { Fail "Couldn't read the repository from the remote URL: $url" }
$owner = $Matches[1]; $oldName = $Matches[2]
$repo = "$owner/$newName"

if ($oldName -ne $newName) {
    Write-Host "Renaming $owner/$oldName to $repo ..." -ForegroundColor Cyan
    gh repo rename $newName --repo "$owner/$oldName" --yes
    if ($LASTEXITCODE -ne 0) { Fail "GitHub didn't rename the repository (see above). You can also rename it on github.com: Settings > General > Repository name." }
    gh repo edit $repo --description "ZapBackTV - a media player for Android TV and phones" | Out-Null
} else {
    Write-Host "The repository is already called $newName."
}
git remote set-url origin "https://github.com/$repo.git"
(Get-Content gradle.properties) -replace '^lumen\.githubRepo=.*', "lumen.githubRepo=$repo" | Set-Content gradle.properties -Encoding ascii
Write-Host "gradle.properties now says lumen.githubRepo=$repo" -ForegroundColor Green

git add -A
git diff --cached --quiet 2>$null
if ($LASTEXITCODE -ne 0) { git commit -m "Rename to ZapBackTV" | Out-Null }
git push origin HEAD
if ($LASTEXITCODE -ne 0) { Fail "Push failed - see the message above." }
Write-Host ""
Write-Host "Done: https://github.com/$repo" -ForegroundColor Green
Write-Host "Next: publish it with  scripts\release.ps1 -Version 2.2.0" -ForegroundColor Green
