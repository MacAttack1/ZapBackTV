#!/usr/bin/env bash
# ZapBackTV - build the APK on Linux or macOS.
#   ./build-apk.sh            release APK (signed with your key in signing/)
#   ./build-apk.sh debug      debug APK
#   ./build-apk.sh release install   ...and install it on a device connected with adb
set -euo pipefail
cd "$(dirname "$0")"
kind="${1:-release}"; [ "$kind" = debug ] || kind=release
task="assemble$(tr '[:lower:]' '[:upper:]' <<< "${kind:0:1}")${kind:1}"

# Gradle 8.11 runs on Java 17-23 only (newer ones fail with just "25.0.4"). Pick a compatible one.
java_major() { "$1/bin/java" -version 2>&1 | awk -F'"' '/version/{split($2,v,"[.+-]"); print (v[1]=="1"?v[2]:v[1]); exit}'; }
pick=""
for p in "/opt/android-studio/jbr" "$HOME/android-studio/jbr" "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
         "${JAVA_HOME:-}" /usr/lib/jvm/*21* /usr/lib/jvm/*17* /Library/Java/JavaVirtualMachines/*21*/Contents/Home /Library/Java/JavaVirtualMachines/*17*/Contents/Home; do
  [ -n "$p" ] && [ -x "$p/bin/java" ] || continue
  m=$(java_major "$p"); [ -n "$m" ] && [ "$m" -ge 17 ] && [ "$m" -le 23 ] && pick="$p" && break
done
[ -n "$pick" ] || { echo "No Java 17-23 found (Java 24+ is too new for this build). Install JDK 21, e.g. from https://adoptium.net"; exit 1; }
export JAVA_HOME="$pick"; export PATH="$JAVA_HOME/bin:$PATH"
echo "Java: $JAVA_HOME"

if [ ! -f local.properties ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [ -z "$sdk" ] && for p in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do [ -d "$p" ] && sdk="$p" && break; done
  [ -d "${sdk:-/nonexistent}" ] || { echo "Android SDK not found: open Android Studio once or set ANDROID_HOME."; exit 1; }
  echo "sdk.dir=$sdk" > local.properties
fi
[ -f signing/keystore.properties ] || echo "Note: no signing key in signing/ - this APK won't install over one signed with your ZapBackTV key."

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
