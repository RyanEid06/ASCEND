"""Static backup/network boundary checks, complemented by device storage tests."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
FORBIDDEN = {
    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES", "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
}
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None
assert application.get(ANDROID + "allowBackup") == "false"
assert application.get(ANDROID + "fullBackupContent") == "@xml/backup_rules"
assert application.get(ANDROID + "dataExtractionRules") == "@xml/data_extraction_rules"
assert application.get(ANDROID + "usesCleartextTraffic") == "false"
for permission in manifest.findall("uses-permission"):
    if permission.get(TOOLS + "node") != "remove":
        assert permission.get(ANDROID + "name") not in FORBIDDEN
for name in ("backup_rules.xml", "data_extraction_rules.xml"):
    document = ET.parse(ROOT / "app/src/main/res/xml" / name).getroot()
    sections = [document] if name == "backup_rules.xml" else list(document)
    for section in sections:
        exclusions = {(e.get("domain"), e.get("path")) for e in section.findall("exclude")}
        assert {(domain, ".") for domain in ("root", "file", "database", "sharedpref", "external")} <= exclusions
print("Local storage backup, permissions and cleartext contract passed")

# Source checks alone miss permissions contributed by transitive AAR manifests.
for argument in sys.argv[1:]:
    apk = Path(argument)
    executable = "aapt.exe" if os.name == "nt" else "aapt"
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", "")
    candidates = list(Path(sdk, "build-tools").glob(f"*/{executable}")) if sdk else []
    candidates.sort(key=lambda path: tuple(int(part) for part in path.parent.name.split(".")), reverse=True)
    aapt = shutil.which(executable) or (str(candidates[0]) if candidates else None)
    assert aapt, "Android SDK aapt required for packaged permission checks"
    output = subprocess.check_output([aapt, "dump", "permissions", str(apk)], text=True)
    permissions = set(re.findall(r"^uses-permission: name='([^']+)'", output, re.MULTILINE))
    forbidden = permissions & FORBIDDEN
    assert not forbidden, f"{apk.name}: forbidden packaged permissions {sorted(forbidden)}"
    print(f"{apk.name}: packaged local-only permissions passed")
