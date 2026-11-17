<#
  One-time GitHub setup for Lumen TV (run from PowerShell or Command Prompt in the project folder):

      powershell -ExecutionPolicy Bypass -File scripts\github-setup.ps1

  It: signs you in to the GitHub CLI if needed, creates the repository named in gradle.properties
  (lumen.githubRepo) if it doesn't exist, stores your signing key as encrypted repository secrets so
  GitHub can build signed releases, and pushes the code.

  Needs: Git (https://git-scm.com) and the GitHub CLI (https://cli.github.com).
#>

# Native tools (git, gh) report normal "not found" answers on stderr. Windows PowerShell 5 turns those
# into fatal errors when ErrorActionPreference is Stop, so this script checks exit codes itself.
$ErrorActionPreference = 'Continue'
Set-Location (Split-Path $PSScriptRoot -Parent)

function Fail($msg) { Write-Host ""; Write-Host "  [!] $msg" -ForegroundColor Red; Write-Host ""; exit 1 }
function Quiet { param([scriptblock]$cmd) & $cmd 2>&1 | Out-Null; return $LASTEXITCODE }

foreach ($tool in 'git', 'gh') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { Fail "$tool isn't installed. See the comment at the top of this script." }
}

# ---- sign in
if ((Quiet { gh auth status }) -ne 0) {
    gh auth login --web --git-protocol https
    if ($LASTEXITCODE -ne 0) { Fail "GitHub sign-in didn't complete." }
}
$login = (gh api user --jq .login 2>$null)
if (-not $login) { Fail "Couldn't read your GitHub username. Try: gh auth login" }
$login = $login.Trim()

# ---- which repository
$propLine = Select-String -Path gradle.properties -Pattern '^lumen\.githubRepo=(.+)$'
$repo = if ($propLine) { $propLine.Matches[0].Groups[1].Value.Trim() } else { "$login/LumenTV" }
$owner, $name = $repo.Split('/', 2)
if ($owner -ne $login) {
    Write-Host "You're signed in to GitHub as '$login', but gradle.properties says '$repo'." -ForegroundColor Yellow
    $answer = Read-Host "Use $login/$name instead? (Y/n)"
    if ($answer -eq '' -or $answer -match '^[Yy]') {
        $repo = "$login/$name"
        (Get-Content gradle.properties) -replace '^lumen\.githubRepo=.*', "lumen.githubRepo=$repo" | Set-Content gradle.properties -Encoding ascii
        Write-Host "Updated gradle.properties. Rebuild the APK afterwards so the app checks this repository." -ForegroundColor Yellow
    }
}
Write-Host "Repository: $repo" -ForegroundColor Cyan

# ---- create it if needed
if ((Quiet { gh repo view $repo }) -ne 0) {
    Write-Host "Creating $repo (public, so the app can check its releases without a password)..."
    gh repo create $repo --public --description "Lumen TV - a media player for Android TV and phones"
    if ($LASTEXITCODE -ne 0) { Fail "Couldn't create $repo. If it belongs to an organization, create it on github.com first, then run this again." }
} else {
    Write-Host "$repo already exists."
}

# ---- signing key -> encrypted repository secrets (never visible in the repo)
if (-not (Test-Path signing\keystore.properties)) { Fail "signing\keystore.properties is missing - you need the Lumen TV signing key first (see BUILDING.md)." }
$pw = (Select-String -Path signing\keystore.properties -Pattern '^storePassword=(.+)$').Matches[0].Groups[1].Value.Trim()
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path signing\lumen-tv.jks)))
gh secret set LUMEN_KEYSTORE_B64 --repo $repo --body $b64
if ($LASTEXITCODE -ne 0) { Fail "Couldn't save the LUMEN_KEYSTORE_B64 secret." }
gh secret set LUMEN_KEYSTORE_PASSWORD --repo $repo --body $pw
if ($LASTEXITCODE -ne 0) { Fail "Couldn't save the LUMEN_KEYSTORE_PASSWORD secret." }
Write-Host "Signing key stored as repository secrets." -ForegroundColor Green

# ---- push the code
if (-not (Test-Path .git)) { git init -b main | Out-Null }
$url = "https://github.com/$repo.git"
if ((Quiet { git remote get-url origin }) -eq 0) { git remote set-url origin $url } else { git remote add origin $url }
if (-not (git config user.name)) { git config user.name $login }
if (-not (git config user.email)) { git config user.email "$login@users.noreply.github.com" }
git add -A
if ((Quiet { git diff --cached --quiet }) -ne 0) { git commit -m "Lumen TV" | Out-Null }
git branch -M main
git push -u origin main
if ($LASTEXITCODE -ne 0) { Fail "Push failed - see the message above." }

Write-Host ""
Write-Host "Done: https://github.com/$repo" -ForegroundColor Green
Write-Host "To publish a release: add notes to CHANGELOG.md, then run scripts\release.ps1 -Version x.y.z" -ForegroundColor Green
