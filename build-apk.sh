#!/usr/bin/env bash
# Lumen TV - build the APK on Linux or macOS.
#   ./build-apk.sh            release APK (signed with your key in signing/)
#   ./build-apk.sh debug      debug APK
#   ./build-apk.sh release install   ...and install it on a device connected with adb
set -euo pipefail
cd "$(dirname "$0")"
kind="${1:-release}"; [ "$kind" = debug ] || kind=release
task="assemble$(tr '[:lower:]' '[:upper:]' <<< "${kind:0:1}")${kind:1}"

if [ -z "${JAVA_HOME:-}" ]; then
  for p in "/opt/android-studio/jbr" "$HOME/android-studio/jbr" "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    [ -x "$p/bin/java" ] && export JAVA_HOME="$p" && break
  done
fi
command -v java >/dev/null 2>&1 || [ -n "${JAVA_HOME:-}" ] || { echo "Java 17+ not found: install Android Studio or set JAVA_HOME."; exit 1; }

if [ ! -f local.properties ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -z "$sdk" ] && for p in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do [ -d "$p" ] && sdk="$p" && break; done
  [ -d "${sdk:-/nonexistent}" ] || { echo "Android SDK not found: open Android Studio once or set ANDROID_HOME."; exit 1; }
  echo "sdk.dir=$sdk" > local.properties
fi
[ -f signing/keystore.properties ] || echo "Note: no signing key in signing/ - this APK won't install over one signed with your Lumen TV key."

chmod +x gradlew
./gradlew "$task" --console=plain
mkdir -p dist
apk=$(ls app/build/outputs/apk/$kind/*.apk | head -1)
cp "$apk" dist/
echo "Done: dist/$(basename "$apk")"
if [ "${2:-}" = install ]; then
  sdk=$(grep '^sdk.dir=' local.properties | cut -d= -f2-)
  "${sdk}/platform-tools/adb" install -r "dist/$(basename "$apk")"
fi
