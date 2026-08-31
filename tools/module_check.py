#!/usr/bin/env python3
import os
import re
import sys
from pathlib import Path

BASE_PKG = "com.maxica.cobass"
SRC_ROOT = Path("app/src/com/maxica/cobass").resolve()

ALLOWED_DEPS = {
    "core": [],
    "model": ["core"],
    "audio": ["core", "model"],
    "plugin": ["core", "model", "audio"],
    "project": ["core", "model", "audio", "plugin"],
    "sequencer": ["core", "model", "audio"],
    "ui": ["core", "model", "audio", "sequencer", "model", "project", "plugin"],
}

IMPORT_REGEX = re.compile(rf"^import\s+{re.escape(BASE_PKG)}\.([a-zA-Z0-9_]+)\.", re.MULTILINE)

def check_boundaries() -> int:
    violations = 0
    if not SRC_ROOT.exists():
        print(f"Error: Source directory {SRC_ROOT} does not exist.")
        return 1

    cwd = Path.cwd().resolve()
    for module_name, allowed in ALLOWED_DEPS.items():
        module_path = SRC_ROOT / module_name
        if not module_path.is_dir():
            continue

        for java_file in module_path.rglob("*.java"):
            resolved_file = java_file.resolve()
            try:
                display_path = resolved_file.relative_to(cwd)
            except ValueError:
                display_path = resolved_file

            content = resolved_file.read_text(encoding="utf-8")
            for match in IMPORT_REGEX.finditer(content):
                target_mod = match.group(1)
                if target_mod == module_name:
                    continue
                if target_mod not in allowed:
                    print(f"\033[91m[BOUNDARY VIOLATION]\033[0m {display_path}")
                    print(f"  Module '{module_name}' is forbidden from importing '{target_mod}'.")
                    violations += 1

    if violations == 0:
        print("\033[92mModule Boundary Verification PASSED.\033[0m")
        return 0
    else:
        print(f"\033[91mModule Boundary Verification FAILED: {violations} violation(s).\033[0m")
        return 1

if __name__ == "__main__":
    sys.exit(check_boundaries())
