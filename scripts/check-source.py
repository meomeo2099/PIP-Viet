from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
base = root / 'PiPInstaller/src/com/geely/pipinstaller'
hashes = json.loads((root / 'tests/upstream-core-hashes.json').read_text())
for name, expected in hashes.items():
    source = (base / name).read_text()
    if name == 'PipService.java':
        source = re.sub(r'"(?:\\.|[^"\\])*"', '""', source)
        source = re.sub(r'\s+', '', source)
    assert hashlib.sha256(source.encode()).hexdigest() == expected, f'Upstream core changed: {name}'
for path in root.rglob('*.xml'):
    if '/build-' not in str(path):
        ET.parse(path)
services = [n.text for n in ET.parse(root / 'source/res/values/config.xml').findall('./string-array/item')]
assert len(services) == 5 and services[-1] == 'com.android.systemui.pip.PipUI'
assert len(set(services)) == 5
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'PiPInstaller/AndroidManifest.xml').getroot()
assert manifest.get(android + 'sharedUserId') == 'android.uid.systemui'
assert manifest.find('uses-sdk').get(android + 'minSdkVersion') == '28'
print('PASS: service state machine, install/boot receivers, resource XML, overlay services and shared UID')
