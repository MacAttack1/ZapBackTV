<#
  One-time GitHub setup for Lumen TV (run from PowerShell in the project folder):

      powershell -ExecutionPolicy Bypass -File scripts\github-setup.ps1

  It: signs you in to the GitHub CLI if needed, creates the repository named in gradle.properties
  (lumen.githubRepo) if it doesn't exist, stores your signing key as encrypted repository secrets so
  GitHub can build signed releases, and pushes the code.

  Needs: Git (https://git-scm.com) and the GitHub CLI (https://cli.github.com).
#>
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

foreach ($tool in 'git', 'gh') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool isn't installed. See the comment at the top of this script." }
}
$repo = ((Select-String -Path gradle.properties -Pattern '^lumen\.githubRepo=(.+)$').Matches[0].Groups[1].Value).Trim()
Write-Host "Repository: $repo" -ForegroundColor Cyan

gh auth status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { gh auth login --web --git-protocol https }

gh repo view $repo 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating $repo (public, so the app can check its releases without a password)..."
    gh repo create $repo --public --description "Lumen TV - a media player for Android TV and phones"
}

# Signing key -> repository secrets (encrypted; never visible in the repo)
if (-not (Test-Path signing\keystore.properties)) { throw "signing\keystore.properties is missing - you need the Lumen TV signing key first (see BUILDING.md)." }
$pw = ((Select-String -Path signing\keystore.properties -Pattern '^storePassword=(.+)$').Matches[0].Groups[1].Value).Trim()
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path signing\lumen-tv.jks)))
gh secret set LUMEN_KEYSTORE_B64 --repo $repo --body $b64
gh secret set LUMEN_KEYSTORE_PASSWORD --repo $repo --body $pw
Write-Host "Signing key stored as repository secrets." -ForegroundColor Green

if (-not (Test-Path .git)) { git init -b main | Out-Null }
if (-not (git remote 2>$null | Select-String -SimpleMatch 'origin')) { git remote add origin "https://github.com/$repo.git" }
git add -A
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { git commit -m "Lumen TV" | Out-Null }
git push -u origin HEAD
Write-Host ""
Write-Host "Done. To publish an update: add notes to CHANGELOG.md, then run scripts\release.ps1 -Version x.y.z" -ForegroundColor Green
