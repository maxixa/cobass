#!/usr/bin/env python3
"""
Cobass Addon Native C++ Compiler (No-Gradle Architecture)
Compiles third-party modular plugins into standalone .so binaries under app/lib/<abi>/
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

def find_compiler(target_abi: str):
    api_level = "34"

    # 1. Desktop / SDK NDK
    ndk_home = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if ndk_home and Path(ndk_home).is_dir():
        llvm_bin = Path(ndk_home) / "toolchains/llvm/prebuilt"
        host_dirs = list(llvm_bin.glob("*"))
        if host_dirs:
            bin_dir = host_dirs[0] / "bin"
            target_prefix = f"aarch64-linux-android{api_level}-clang++" if target_abi == "arm64-v8a" else f"armv7a-linux-androideabi{api_level}-clang++"
            compiler = bin_dir / target_prefix
            if compiler.exists():
                return str(compiler), []

    # 2. Termux Clang
    clang_path = shutil.which("clang++")
    if clang_path:
        target_triple = f"aarch64-linux-android{api_level}" if target_abi == "arm64-v8a" else f"armv7a-linux-androideabi{api_level}"
        return clang_path, [
            "-fPIC",
            "-target", target_triple,
            "-D_LIBCPP_HAS_NO_PTHREAD_COND_CLOCKWAIT",
            "-D_LIBCPP_ENABLE_CXX20_REMOVED_FEATURES",
            "-Wno-macro-redefined"
        ]

    return None, []

def build_all_addons(abi: str = "arm64-v8a", out_dir: Path = Path("app/lib")) -> bool:
    compiler, extra_flags = find_compiler(abi)
    if not compiler:
        print(f"\033[91mError: Clang++ compiler not found for {abi}.\033[0m")
        return False

    addons_dir = Path("addons")
    if not addons_dir.is_dir():
        print("  [*] No addons directory found. Skipping.")
        return True

    target_abi_dir = out_dir / abi
    target_abi_dir.mkdir(parents=True, exist_ok=True)

    success = True
    for addon in sorted(addons_dir.iterdir()):
        if not addon.is_dir():
            continue

        src_dir = addon / "src"
        cpp_files = list(src_dir.glob("*.cpp"))
        if not cpp_files:
            continue

        # Format output library name: libcobass_plugin_<name>.so
        clean_name = addon.name.replace("-", "_")
        target_so = target_abi_dir / f"libcobass_plugin_{clean_name}.so"

        cmd = [
            compiler,
            "-std=c++20",
            "-shared",
            "-fPIC",
            "-O3",
            "-DNDEBUG",
            "-Iapp/native/include",
            "-Iapp/native/dsp",
            "-Iapp/native/plugin",
            *extra_flags,
            *[str(f) for f in cpp_files],
            "-o", str(target_so),
            "-lm"
        ]

        print(f"[*] Compiling Plugin '{addon.name}' -> {target_so.name}...")
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if res.returncode != 0:
            print(f"\033[91m[FAILED] Addon {addon.name} compilation failed:\033[0m\n{res.stderr}")
            success = False
        else:
            size_kb = target_so.stat().st_size / 1024
            print(f"\033[92m[OK] Built Plugin {target_so.name} ({size_kb:.1f} KB)\033[0m")

    return success

if __name__ == "__main__":
    abi = sys.argv[1] if len(sys.argv) > 1 else "arm64-v8a"
    if not build_all_addons(abi):
        sys.exit(1)
