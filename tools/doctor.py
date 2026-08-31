#!/usr/bin/env python3
"""
BeatForge Environment & Toolchain Diagnostics
Supports Desktop SDK/NDK and Termux on Android.
"""
import os
import sys
import shutil
import subprocess
from pathlib import Path

def print_status(component: str, ok: bool, message: str):
    status = "\033[92m[OK]\033[0m" if ok else "\033[91m[FAIL]\033[0m"
    print(f"{status} {component.ljust(28)} : {message}")

def check_python():
    v = sys.version_info
    ok = v.major == 3 and v.minor >= 10
    print_status("Python Runtime", ok, f"Python {v.major}.{v.minor}.{v.micro}")
    return ok

def check_command(name: str, cmd: list[str]) -> bool:
    path = shutil.which(cmd[0])
    if not path:
        print_status(name, False, f"Not found in PATH ({cmd[0]})")
        return False
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
        out = (res.stdout + " " + res.stderr).strip().splitlines()
        first_line = out[0] if out else "Available"
        print_status(name, True, f"{first_line} ({path})")
        return True
    except Exception as e:
        print_status(name, False, f"Execution failed: {e}")
        return False

def check_android_sdk():
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_root or not Path(sdk_root).is_dir():
        print_status("Android SDK Root", False, "ANDROID_HOME not set or invalid")
        return False
    print_status("Android SDK Root", True, sdk_root)

    # Check platforms
    plat_dir = Path(sdk_root) / "platforms"
    android_jars = list(plat_dir.glob("android-*/android.jar")) if plat_dir.is_dir() else []
    sdk_ok = True
    if not android_jars:
        print_status("android.jar Platform", False, f"Missing in {plat_dir}")
        sdk_ok = False
    else:
        latest_plat = sorted(android_jars, reverse=True)[0]
        print_status("android.jar Platform", True, str(latest_plat))

    # Check tools (aapt2 or aapt, d8, apksigner)
    for tool in ["aapt2", "d8", "apksigner"]:
        found = shutil.which(tool)
        if not found and tool == "aapt2":
            found = shutil.which("aapt")
        if found:
            print_status(f"SDK Tool: {tool}", True, found)
        else:
            print_status(f"SDK Tool: {tool}", False, "Missing in PATH")
            sdk_ok = False

    return sdk_ok

def check_ndk():
    # 1. Desktop / Standard NDK check
    ndk_home = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("NDK_HOME")
    if ndk_home and Path(ndk_home).is_dir():
        print_status("Native Toolchain (NDK)", True, ndk_home)
        return True

    # 2. Termux native Clang toolchain check
    clang_path = shutil.which("clang")
    if clang_path and ("/com.termux/" in clang_path or "/usr/bin/clang" in clang_path):
        print_status("Native Toolchain (Clang)", True, f"Termux Native Clang: {clang_path}")
        return True

    print_status("Native Toolchain", False, "Neither ANDROID_NDK_HOME nor Termux Clang found")
    return False

def main():
    print("=" * 60)
    print("BeatForge Toolchain Doctor")
    print("=" * 60)

    py_ok = check_python()
    java_ok = check_command("Java Compiler (javac)", ["javac", "-version"])
    sdk_ok = check_android_sdk()
    ndk_ok = check_ndk()

    print("=" * 60)
    if py_ok and java_ok and sdk_ok and ndk_ok:
        print("\033[92mAll toolchain requirements are met. Ready for building.\033[0m")
        sys.exit(0)
    else:
        print("\033[91mToolchain requirements missing. Resolve the above issues.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
