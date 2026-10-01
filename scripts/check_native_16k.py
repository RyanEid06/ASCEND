"""Inventory APK native libraries and reject ELF load segments below 16 KiB alignment."""

import pathlib
import os
import shutil
import struct
import subprocess
import sys
import zipfile


PAGE_SIZE = 16 * 1024


def load_alignments(data: bytes) -> list[int]:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("not a valid ELF file")
    elf_class, byte_order = data[4], data[5]
    if byte_order not in (1, 2) or elf_class not in (1, 2):
        raise ValueError("unsupported ELF format")
    endian = "<" if byte_order == 1 else ">"
    if elf_class == 2:
        header_offset, entry_fmt = 32, endian + "IIQQQQQQ"
        phoff = struct.unpack_from(endian + "Q", data, header_offset)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 54)
        align_index = 7
    else:
        header_offset, entry_fmt = 28, endian + "IIIIIIII"
        phoff = struct.unpack_from(endian + "I", data, header_offset)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 42)
        align_index = 7
    expected_size = struct.calcsize(entry_fmt)
    if phentsize < expected_size or phoff + phentsize * phnum > len(data):
        raise ValueError("truncated ELF program headers")
    return [
        struct.unpack_from(entry_fmt, data, phoff + index * phentsize)[align_index]
        for index in range(phnum)
        if struct.unpack_from(endian + "I", data, phoff + index * phentsize)[0] == 1
    ]


def main(apk: pathlib.Path) -> None:
    with zipfile.ZipFile(apk) as archive:
        libraries = sorted(name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so"))
        for name in libraries:
            alignments = load_alignments(archive.read(name))
            if not alignments or any(value < PAGE_SIZE for value in alignments):
                raise SystemExit(f"{name}: PT_LOAD alignment below 16 KiB: {alignments}")
            print(f"{name}: PT_LOAD alignments {alignments}")
    print(f"Native library inventory: {len(libraries)} files")
    if libraries:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", "")
        candidates = sorted(pathlib.Path(sdk, "build-tools").glob("*/zipalign"), reverse=True) if sdk else []
        zipalign = shutil.which("zipalign") or (str(candidates[0]) if candidates else None)
        if not zipalign:
            raise SystemExit("zipalign is required when native libraries are present")
        result = subprocess.run([zipalign, "-c", "-P", "16", "-v", "4", str(apk)], check=False)
        if result.returncode:
            raise SystemExit("APK native library ZIP alignment check failed")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check_native_16k.py path/to/app.apk")
    main(pathlib.Path(sys.argv[1]))
