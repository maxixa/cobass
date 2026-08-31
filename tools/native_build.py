#!/usr/bin/env python3
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

def find_compiler(target_abi: str):
    api_level = "34"

    # 1. Desktop NDK
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

    # 2. Termux Native Clang
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

def copy_libcxx_runtime(out_abi_dir: Path):
    prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
    candidates = [
        Path(prefix) / "lib/libc++_shared.so",
        Path(prefix) / "lib/aarch64-linux-android/libc++_shared.so"
    ]
    for cand in candidates:
        if cand.exists():
            shutil.copy2(cand, out_abi_dir / "libc++_shared.so")
            print(f"  [+] Bundled C++ runtime: {cand.name}")
            return True
    return False

def build_native(abi: str, out_dir: Path, is_release: bool = True) -> bool:
    print(f"[*] Compiling native audio engine for ABI: {abi}...")

    compiler, extra_flags = find_compiler(abi)
    if not compiler:
        print(f"\033[91mError: Clang++ compiler not found for {abi}.\033[0m")
        return False

    sources = [
        "app/native/AudioEngine.cpp",
        "app/native/jni_bridge.cpp"
    ]

    include_flags = [
        "-Iapp/native",
        "-Iapp/native/dsp",
        "-Iapp/native/sequencer",
        "-Iapp/native/plugin",
        "-Iapp/native/include"
    ]

    opt_flags = ["-O3", "-DNDEBUG"] if is_release else ["-O0", "-g", "-DDEBUG"]

    lib_flags = [
        "-laaudio",
        "-llog",
        "-landroid",
        "-lm"
    ]

    abi_out = out_dir / abi
    abi_out.mkdir(parents=True, exist_ok=True)
    target_so = abi_out / "libcobass_audio.so"

    cmd = [
        compiler,
        "-std=c++20",
        "-shared",
        *extra_flags,
        *opt_flags,
        *include_flags,
        *sources,
        "-o", str(target_so),
        *lib_flags
    ]

    print(f"    Compiler: {compiler}")
    print(f"    Target:   {target_so}")

    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"\033[91m[NATIVE BUILD FAILED]\033[0m\n{res.stderr}")
        return False

    copy_libcxx_runtime(abi_out)

    size_kb = target_so.stat().st_size / 1024
    print(f"\033[92m[OK] Built standalone libcobass_audio.so ({size_kb:.1f} KB)\033[0m")
    return True

def main():
    parser = argparse.ArgumentParser(description="Cobass Native C++ Builder")
    parser.add_argument("--out", default="app/lib", help="Output directory for .so libraries")
    parser.add_argument("--variant", default="release", choices=["debug", "release"], help="Build variant")
    args = parser.parse_args()

    out_dir = Path(args.out)
    is_release = args.variant == "release"

    success = build_native("arm64-v8a", out_dir, is_release)
    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()
