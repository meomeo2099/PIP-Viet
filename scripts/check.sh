#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash -n scripts/build.sh
bash -n scripts/check-signing-env.sh
build="$(mktemp -d)"
trap 'rm -rf "$build"' EXIT
javac --release 8 -encoding UTF-8 -d "$build" PiPInstaller/src/com/geely/pipinstaller/CompatibilityPolicy.java tests/CompatibilityPolicyTest.java
java -cp "$build" com.geely.pipinstaller.CompatibilityPolicyTest
python3 scripts/check-source.py

javac --release 8 -encoding UTF-8 -d "$build" PiPInstaller/src/com/geely/pipinstaller/DashboardCallerPolicy.java tests/DashboardCallerPolicyTest.java
java -cp "$build" com.geely.pipinstaller.DashboardCallerPolicyTest
