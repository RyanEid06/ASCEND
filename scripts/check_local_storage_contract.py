"""Static backup/network boundary checks, complemented by device storage tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
assert application is not None
assert application.get(ANDROID + "allowBackup") == "false"
assert application.get(ANDROID + "fullBackupContent") == "@xml/backup_rules"
assert application.get(ANDROID + "dataExtractionRules") == "@xml/data_extraction_rules"
assert application.get(ANDROID + "usesCleartextTraffic") == "false"
for permission in manifest.findall("uses-permission"):
    assert permission.get(ANDROID + "name") not in {
        "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES", "android.permission.INTERNET",
    }
for name in ("backup_rules.xml", "data_extraction_rules.xml"):
    document = ET.parse(ROOT / "app/src/main/res/xml" / name).getroot()
    sections = [document] if name == "backup_rules.xml" else list(document)
    for section in sections:
        exclusions = {(e.get("domain"), e.get("path")) for e in section.findall("exclude")}
        assert {(domain, ".") for domain in ("root", "file", "database", "sharedpref", "external")} <= exclusions
print("Local storage backup, permissions and cleartext contract passed")
