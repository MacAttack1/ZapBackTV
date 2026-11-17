#!/usr/bin/env bash
# Publish a ZapBackTV update from Linux/macOS: add a "## X.Y.Z" section to CHANGELOG.md, then
#   scripts/release.sh X.Y.Z
set -euo pipefail
cd "$(dirname "$0")/.."
v="${1:?usage: scripts/release.sh X.Y.Z}"
[[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Use a version like 2.1.1"; exit 1; }
awk -v v="$v" '/^## /{p=($2==v||$2=="v"v||$2=="["v"]"); next} p' CHANGELOG.md | grep -q '[^[:space:]]' \
  || { echo "Add a '## $v' section with notes to CHANGELOG.md first."; exit 1; }
sed -i.bak "s/^versionName=.*/versionName=$v/" version.properties && rm -f version.properties.bak
git add -A && git commit -m "ZapBackTV $v" && git tag "v$v"
git push origin HEAD && git push origin "v$v"
echo "Pushed v$v - GitHub Actions is building and publishing it."
