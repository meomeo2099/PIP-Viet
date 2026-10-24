#!/usr/bin/env bash
set -euo pipefail
# Both APKs are rebuilt and signed with the same supplied key.
root="$(cd "$(dirname "$0")/.." && pwd)"
if [[ "${1:-}" != '--check' ]]; then bash "$root/scripts/check-signing-env.sh"; fi
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$sdk" ]] || { echo 'Cần đặt ANDROID_SDK_ROOT hoặc ANDROID_HOME.' >&2; exit 1; }
bt="$sdk/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
android="$sdk/platforms/android-${COMPILE_API:-35}/android.jar"
for path in "$bt/aapt" "$bt/zipalign" "$bt/lib/apksigner.jar" "$bt/lib/d8.jar" "$android"; do
  [[ -f "$path" ]] || { echo "Thiếu công cụ: $path" >&2; exit 1; }
done
build="$(mktemp -d "$root/PiPInstaller/build-XXXXXXXX")"
trap 'rm -rf "$build"' EXIT
mkdir -p "$build"/{overlay,assets,generated,classes,dex} "$root/PiPInstaller/dist"
if [[ "${1:-}" == '--check' ]]; then
  # Disposable CI key. No development APK is copied to dist or published.
  keytool -genkeypair -keystore "$build/check.jks" -storepass check-only -keypass check-only \
    -alias check -dname 'CN=CI Check Only' -keyalg RSA -keysize 2048 -validity 2 >/dev/null 2>&1
  keyfile="$build/check.jks"; export PIP_STORE_PASS=check-only PIP_KEY_PASS=check-only; aliasname=check
else
  printf '%s' "$THT_SIGN_KEYSTORE_BASE64" | base64 --decode > "$build/sign.jks"
  chmod 600 "$build/sign.jks"
  keyfile="$build/sign.jks"; aliasname="$THT_SIGN_KEY_ALIAS"
  export PIP_STORE_PASS="$THT_SIGN_STORE_PASSWORD" PIP_KEY_PASS="$THT_SIGN_KEY_PASSWORD"
fi
sign_apk() {
  local unsigned="$1" output="$2"
  "$bt/zipalign" -f 4 "$unsigned" "$build/aligned.apk"
  java -jar "$bt/lib/apksigner.jar" sign --ks "$keyfile" --ks-key-alias "$aliasname" \
    --ks-pass env:PIP_STORE_PASS --key-pass env:PIP_KEY_PASS --out "$output" "$build/aligned.apk"
  java -jar "$bt/lib/apksigner.jar" verify "$output"
  if [[ "$check_mode" != true ]]; then
    local expected actual
    expected="$(printf '%s' "$THT_SIGN_CERT_SHA256" | tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"
    [[ "$expected" =~ ^[0-9A-F]{64}$ ]] || { echo 'THT_SIGN_CERT_SHA256 không hợp lệ.' >&2; exit 1; }
    actual="$(java -jar "$bt/lib/apksigner.jar" verify --print-certs "$output" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:lower:]' '[:upper:]')"
    [[ "$actual" == "$expected" ]] || { echo 'Chứng chỉ APK không khớp THT_SIGN_CERT_SHA256.' >&2; exit 1; }
  fi
}
check_mode=false; [[ "${1:-}" != '--check' ]] || check_mode=true
"$bt/aapt" package -f -M "$root/source/AndroidManifest.xml" -S "$root/source/res" -I "$android" -F "$build/overlay/unsigned.apk"
sign_apk "$build/overlay/unsigned.apk" "$build/assets/pip-overlay-platform.apk"
overlay_hash="$(sha256sum "$build/assets/pip-overlay-platform.apk" | cut -d' ' -f1 | tr '[:lower:]' '[:upper:]')"
mkdir -p "$build/generated/com/geely/pipinstaller"
cat > "$build/generated/com/geely/pipinstaller/BuildConfig.java" <<JAVA
package com.geely.pipinstaller;
final class BuildConfig { static final String OVERLAY_SHA256 = "$overlay_hash"; }
JAVA
"$bt/aapt" package -f -m -J "$build/generated" -M "$root/PiPInstaller/AndroidManifest.xml" \
  -S "$root/PiPInstaller/res" -A "$build/assets" -I "$android" -F "$build/resources.apk"
find "$root/PiPInstaller/src" "$build/generated" -name '*.java' -print0 |
  while IFS= read -r -d '' source_file; do printf '"%s"\n' "$source_file"; done > "$build/sources.txt"
javac --release 8 -encoding UTF-8 -classpath "$android" -d "$build/classes" @"$build/sources.txt"
jar cf "$build/classes.jar" -C "$build/classes" .
java -Xmx512m -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 28 --lib "$android" --output "$build/dex" "$build/classes.jar"
(cd "$build/dex" && "$bt/aapt" add "$build/resources.apk" classes.dex)
sign_apk "$build/resources.apk" "$build/PIP-Viet-1.0.1.apk"
if [[ "$check_mode" == true ]]; then
  echo 'Đã biên dịch và kiểm tra cả hai APK. Không xuất APK dùng khóa kiểm tra.'
else
  cp "$build/PIP-Viet-1.0.1.apk" "$root/PiPInstaller/dist/PIP-Viet-1.0.1.apk"
  cp "$build/assets/pip-overlay-platform.apk" "$root/PiPInstaller/dist/pip-overlay-platform.apk"
  (cd "$root/PiPInstaller/dist" && sha256sum PIP-Viet-1.0.1.apk pip-overlay-platform.apk > SHA256SUMS)
  echo 'Đã tạo PiPInstaller/dist/PIP-Viet-1.0.1.apk bằng THT_SIGN_*.'
fi
