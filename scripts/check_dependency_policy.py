"""Reject dynamic dependency versions; lockfile generation follows resolved P0 build."""

import pathlib
import re
import sys
import tomllib


def check_version(name: str, version: str) -> None:
    if not isinstance(version, str) or not re.fullmatch(r"\d+(?:\.\d+)*(?:[-.][A-Za-z0-9]+)*", version):
        raise SystemExit(f"Unpinned/dynamic version: {name}={version}")
    if any(marker in version.lower() for marker in ("snapshot", "latest", "+")):
        raise SystemExit(f"Dynamic dependency version: {name}={version}")


def main(path: pathlib.Path) -> None:
    catalogue = tomllib.loads(path.read_text(encoding="utf-8"))
    versions = catalogue.get("versions", {})
    if not versions:
        raise SystemExit("No pinned dependency versions found")
    for name, version in versions.items():
        check_version(name, version)
    for group in ("libraries", "plugins"):
        for name, item in catalogue.get(group, {}).items():
            specification = item.get("version")
            if specification is None:
                # Compose BOM managed libraries intentionally omit their own version.
                if not name.startswith("androidx-compose-"):
                    raise SystemExit(f"Dependency has no explicit/BOM version: {name}")
            elif isinstance(specification, dict):
                reference = specification.get("ref")
                if reference not in versions:
                    raise SystemExit(f"Unknown version reference: {name}={reference}")
            else:
                check_version(name, specification)
    print(f"Checked {len(versions)} pinned dependency versions")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check_dependency_policy.py gradle/libs.versions.toml")
    main(pathlib.Path(sys.argv[1]))
