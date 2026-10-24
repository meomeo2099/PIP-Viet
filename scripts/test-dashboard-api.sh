#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set Android SDK}" "${PIP_QA_DEPS:?Directory of Robolectric/JUnit jars}"
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
mkdir -p "$build/gen/com/geely/pipinstaller" "$build/classes"
# Fixture constants only. Real APK/overlay integrity is independently checked by build.sh --check.
cat > "$build/gen/com/geely/pipinstaller/R.java" <<'JAVA'
package com.geely.pipinstaller;final class R {static final class drawable {static final int ic_pip=1;}}
JAVA
cat > "$build/gen/com/geely/pipinstaller/BuildConfig.java" <<'JAVA'
package com.geely.pipinstaller;final class BuildConfig {static final String OVERLAY_SHA256="fixture";}
JAVA
python3 - "$PIP_QA_DEPS" "$build" <<'PYQA'
import sys,zipfile
from pathlib import Path
for item in Path(sys.argv[1]).glob('*.aar'):
 with zipfile.ZipFile(item) as archive:
  Path(sys.argv[2],item.stem+'.jar').write_bytes(archive.read('classes.jar'))
PYQA
cp="$ANDROID_HOME/platforms/android-${COMPILE_API:-34}/android.jar"
for file in "$PIP_QA_DEPS"/*.jar "$build"/*.jar; do cp="$cp:$file"; done
javac -encoding UTF-8 -cp "$cp" -d "$build/classes" PiPInstaller/src/com/geely/pipinstaller/*.java "$build/gen/com/geely/pipinstaller/"*.java tests/native/*.java
java -Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2 -cp "$build/classes:$cp" org.junit.runner.JUnitCore com.geely.pipinstaller.DashboardApiServiceTest
