"""adb instrumentation can exit zero even when a test fails; require its success summary."""
from pathlib import Path
import re
import sys

output = Path(sys.argv[1]).read_text(encoding="utf-8")
if not re.search(r"OK \(1 test\)", output) or any(
    marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")
):
    raise SystemExit("Separate-process storage instrumentation did not pass")
print("Separate-process storage instrumentation passed")
