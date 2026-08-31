#!/usr/bin/env python3
"""
Cobass Deterministic No-Gradle APK Builder & Signer (Target API 34)
Includes automated D8 classpath deduplication.
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

def run_cmd(cmd: list[str], step_name: str) -> None:
    print(f"  [+] {step_name}...")
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if res.returncode != 0:
        print(f"\033[91m[{step_name.upper()} FAILED]\033[0m")
        print(f"Command: {' '.join(cmd)}")
        print(f"Error output:\n{res.stderr}\n{res.stdout}")
        sys.exit(1)

def find_tool(name: str) -> str:
    path = shutil.which(name)
    if path: return path

    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_root:
        bt_dir = Path(sdk_root) / "build-tools"
        if bt_dir.is_dir():
            for b in sorted(bt_dir.iterdir(), reverse=True):
                cand = b / name
                if cand.exists(): return str(cand)
    return name

def get_android_jar() -> str:
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk_root:
        plat_dir = Path(sdk_root) / "platforms"
        jars = sorted(list(plat_dir.glob("android-*/android.jar")), reverse=True)
        if jars: return str(jars[0])
    print("\033[91mError: android.jar not found in SDK platforms.\033[0m")
    sys.exit(1)

def ensure_keystore(keystore_path: Path) -> None:
    if not keystore_path.exists():
        print("  [*] Generating debug signing keystore...")
        keystore_path.parent.mkdir(parents=True, exist_ok=True)
        cmd = [
            "keytool", "-genkeypair", "-v",
            "-keystore", str(keystore_path),
            "-alias", "androiddebugkey",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-storepass", "android",
            "-keypass", "android",
            "-dname", "CN=Cobass Debug,O=Maxica,C=US"
        ]
        run_cmd(cmd, "Generating Debug Keystore")

def main():
    parser = argparse.ArgumentParser(description="Cobass No-Gradle APK Builder")
    parser.add_argument("--variant", default="release", choices=["debug", "release"], help="Build variant")
    parser.add_argument("--out", default="out/apk", help="Output directory for signed APK")
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    android_jar = get_android_jar()
    aapt2 = find_tool("aapt2")
    d8 = find_tool("d8")
    zipalign = find_tool("zipalign")
    apksigner = find_tool("apksigner")

    work_dir = Path("out/build_tmp")
    if work_dir.exists(): shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)

    gen_dir = Path("out/generated")
    classes_dir = work_dir / "classes"
    classes_dir.mkdir(parents=True, exist_ok=True)
    dex_dir = work_dir / "dex"
    dex_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 65)
    print(f"Cobass No-Gradle Packaging [{args.variant.upper()}] (Target API 34)")
    print("=" * 65)

    # 1. Compile App Resources
    compiled_res_zips = []
    app_res = Path("app/res")
    if app_res.is_dir():
        app_zip = work_dir / "app_res.zip"
        run_cmd([aapt2, "compile", "--dir", str(app_res), "-o", str(app_zip)], "Compiling App Resources")
        compiled_res_zips.append(str(app_zip))

    # Exploded AAR Resources
    res_dirs_file = gen_dir / "res_dirs.txt"
    if res_dirs_file.exists():
        with open(res_dirs_file, "r", encoding="utf-8") as f:
            for idx, line in enumerate(f):
                r_dir = line.strip()
                if r_dir and Path(r_dir).is_dir() and any(Path(r_dir).iterdir()):
                    dep_zip = work_dir / f"dep_res_{idx}.zip"
                    res = subprocess.run([aapt2, "compile", "--dir", r_dir, "-o", str(dep_zip)], capture_output=True)
                    if res.returncode == 0 and dep_zip.exists():
                        compiled_res_zips.append(str(dep_zip))

    # 2. Link Resources
    extra_pkgs = ""
    extra_pkgs_file = gen_dir / "extra_packages.txt"
    if extra_pkgs_file.exists():
        extra_pkgs = extra_pkgs_file.read_text(encoding="utf-8").strip()

    base_apk = work_dir / "base_linked.apk"
    link_cmd = [
        aapt2, "link",
        "-I", android_jar,
        "--min-sdk-version", "26",
        "--target-sdk-version", "34",
        "--version-code", "1",
        "--version-name", "0.1.0",
        "--manifest", "app/AndroidManifest.xml",
        "--java", str(gen_dir),
        "-o", str(base_apk),
        "--auto-add-overlay"
    ]
    if extra_pkgs:
        link_cmd.extend(["--extra-packages", extra_pkgs])
    link_cmd.extend(compiled_res_zips)

    run_cmd(link_cmd, "Linking Resources (aapt2)")

    # 3. Compile Java Sources
    classpath_entries = [android_jar]
    classpath_file = gen_dir / "classpath.txt"
    if classpath_file.exists():
        cp_data = classpath_file.read_text(encoding="utf-8").strip()
        if cp_data:
            classpath_entries.extend(cp_data.split(os.pathsep))

    java_sources = [str(p) for p in Path("app/src").rglob("*.java")]
    java_sources.extend([str(p) for p in gen_dir.rglob("*.java")])

    javac_cmd = [
        "javac",
        "-source", "17",
        "-target", "17",
        "-encoding", "UTF-8",
        "-cp", os.pathsep.join(classpath_entries),
        "-d", str(classes_dir),
        *java_sources
    ]
    run_cmd(javac_cmd, f"Compiling {len(java_sources)} Java Sources (javac)")

    # 4. Dex Bytecode with Deduplication Filter
    class_files = [str(p) for p in classes_dir.rglob("*.class")]
    raw_jar_deps = [entry for entry in classpath_entries if entry.endswith(".jar") and entry != android_jar and Path(entry).exists()]
    
    # Deduplicate JAR dependencies and avoid legacy kotlin-stdlib-jdk7/8 conflicts
    unique_jars = []
    has_modern_kotlin = any("kotlin-stdlib-" in j and not ("-jdk7" in j or "-jdk8" in j) for j in raw_jar_deps)
    
    for jar in raw_jar_deps:
        if has_modern_kotlin and ("kotlin-stdlib-jdk7" in jar or "kotlin-stdlib-jdk8" in jar):
            continue
        if jar not in unique_jars:
            unique_jars.append(jar)

    d8_cmd = [
        d8,
        "--min-api", "26",
        "--output", str(dex_dir),
        *class_files,
        *unique_jars
    ]
    if args.variant == "release":
        d8_cmd.append("--release")

    run_cmd(d8_cmd, "Compiling DEX Bytecode (d8)")

    # 5. Assemble APK
    unaligned_apk = work_dir / "unaligned.apk"
    shutil.copy2(base_apk, unaligned_apk)

    with zipfile.ZipFile(unaligned_apk, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        for dex_file in dex_dir.glob("*.dex"):
            zf.write(dex_file, arcname=dex_file.name)

        native_root = Path("app/lib")
        if native_root.is_dir():
            for so_file in native_root.rglob("*.so"):
                abi = so_file.parent.name
                arcname = f"lib/{abi}/{so_file.name}"
                zf.write(so_file, arcname=arcname)
                print(f"    Embedded Native Lib: {arcname}")

    # 6. ZipAlign
    aligned_apk = work_dir / "aligned.apk"
    run_cmd([zipalign, "-f", "4", str(unaligned_apk), str(aligned_apk)], "Aligning APK (zipalign)")

    # 7. Sign APK
    keystore = Path("config/debug.keystore")
    ensure_keystore(keystore)

    final_apk = out_dir / f"Cobass-{args.variant}.apk"
    sign_cmd = [
        apksigner, "sign",
        "--ks", str(keystore),
        "--ks-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey",
        "--key-pass", "pass:android",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", str(final_apk),
        str(aligned_apk)
    ]
    run_cmd(sign_cmd, "Signing APK with V1/V2/V3 (apksigner)")

    # 8. Verify
    verify_cmd = [apksigner, "verify", "--verbose", str(final_apk)]
    run_cmd(verify_cmd, "Verifying Cryptographic Signature")

    apk_size_mb = final_apk.stat().st_size / (1024 * 1024)

    print("=" * 65)
    print("\033[92mBUILD SUCCESSFUL!\033[0m")
    print(f"Package:         com.maxica.cobass")
    print(f"Output APK:      {final_apk.resolve()}")
    print(f"File Size:       {apk_size_mb:.2f} MB")
    print(f"ABI Supported:   arm64-v8a (AAudio Real-Time Engine)")
    print("=" * 65)

if __name__ == "__main__":
    main()
