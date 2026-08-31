#!/usr/bin/env python3
"""
BeatForge AAR/JAR Expander and Build Graph Generator
Strictly lockfile-driven: only extracts artifacts listed in resolved.lock.json,
preventing duplicate class conflicts during d8 dexing.
"""
import argparse
import json
import os
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

def extract_aar(aar_path: Path, dest_dir: Path) -> dict:
    info = {
        "classes_jar": None,
        "res_dir": None,
        "package_name": None,
        "assets_dir": None,
        "jni_libs": []
    }
    
    if dest_dir.exists():
        shutil.rmtree(dest_dir)
    dest_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(aar_path, "r") as zf:
        zf.extractall(dest_dir)

    # 1. Classes JAR
    classes_jar = dest_dir / "classes.jar"
    if classes_jar.exists():
        info["classes_jar"] = str(classes_jar.resolve())

    # Check for additional jars inside libs/
    embedded_libs = dest_dir / "libs"
    if embedded_libs.is_dir():
        for extra_jar in embedded_libs.glob("*.jar"):
            info["classes_jar"] += os.pathsep + str(extra_jar.resolve())

    # 2. Resource Directory
    res_dir = dest_dir / "res"
    if res_dir.is_dir() and any(res_dir.iterdir()):
        info["res_dir"] = str(res_dir.resolve())

    # 3. AndroidManifest package name
    manifest_file = dest_dir / "AndroidManifest.xml"
    if manifest_file.exists():
        try:
            tree = ET.parse(manifest_file)
            root = tree.getroot()
            pkg = root.attrib.get("package")
            if pkg:
                info["package_name"] = pkg
        except Exception:
            pass

    # 4. Assets
    assets_dir = dest_dir / "assets"
    if assets_dir.is_dir() and any(assets_dir.iterdir()):
        info["assets_dir"] = str(assets_dir.resolve())

    # 5. JNI shared libraries
    jni_dir = dest_dir / "jni"
    if jni_dir.is_dir():
        for so_file in jni_dir.rglob("*.so"):
            info["jni_libs"].append(str(so_file.resolve()))

    return info

def main():
    parser = argparse.ArgumentParser(description="BeatForge Lockfile-Driven AAR Expander")
    parser.add_argument("--lock", default="libs/resolved.lock.json", help="Path to resolved.lock.json")
    parser.add_argument("--in-dir", default="libs/downloaded", help="Input directory containing AAR/JARs")
    parser.add_argument("--out-dir", default="libs/exploded", help="Output exploded directory")
    parser.add_argument("--generated", default="out/generated", help="Output directory for build metadata")
    parser.add_argument("--app-lib", default="app/lib", help="Target app native library directory")
    args = parser.parse_args()

    lock_file = Path(args.lock)
    in_dir = Path(args.in_dir)
    out_dir = Path(args.out_dir)
    gen_dir = Path(args.generated)
    app_lib_dir = Path(args.app_lib)

    if not lock_file.exists():
        print(f"Error: {lock_file} not found.")
        sys.exit(1)

    with open(lock_file, "r", encoding="utf-8") as f:
        lock_data = json.load(f)

    artifacts = lock_data.get("artifacts", [])

    # Clean previous output
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    gen_dir.mkdir(parents=True, exist_ok=True)

    classpath_entries = []
    res_dirs = []
    extra_packages = set()
    jni_map = {}

    print(f"[*] Expanding {len(artifacts)} locked artifacts from {lock_file}...")

    for item in artifacts:
        artifact_name = item["artifact"]
        version = item["version"]
        packaging = item["packaging"]
        filename = f"{artifact_name}-{version}.{packaging}"
        file_path = in_dir / filename

        if not file_path.exists():
            continue

        if packaging == "aar":
            dest = out_dir / f"{artifact_name}-{version}"
            info = extract_aar(file_path, dest)

            if info["classes_jar"]:
                classpath_entries.extend(info["classes_jar"].split(os.pathsep))
            if info["res_dir"]:
                res_dirs.append(info["res_dir"])
            if info["package_name"] and info["res_dir"]:
                extra_packages.add(info["package_name"])

            for so_path in info["jni_libs"]:
                so_p = Path(so_path)
                abi = so_p.parent.name
                target_abi_dir = app_lib_dir / abi
                target_abi_dir.mkdir(parents=True, exist_ok=True)
                shutil.copy2(so_p, target_abi_dir / so_p.name)
                jni_map.setdefault(abi, []).append(str(target_abi_dir / so_p.name))

            sys.stdout.write(f"\r  [+] Exploded AAR: {filename}".ljust(75))
            sys.stdout.flush()

        elif packaging == "jar":
            classpath_entries.append(str(file_path.resolve()))
            sys.stdout.write(f"\r  [+] Indexed JAR:  {filename}".ljust(75))
            sys.stdout.flush()

    print("\n[*] Writing build graph artifacts...")

    # Classpath
    with open(gen_dir / "classpath.txt", "w", encoding="utf-8") as f:
        f.write(os.pathsep.join(classpath_entries))

    # Res Dirs
    with open(gen_dir / "res_dirs.txt", "w", encoding="utf-8") as f:
        for r in res_dirs:
            f.write(f"{r}\n")

    # Extra Packages
    with open(gen_dir / "extra_packages.txt", "w", encoding="utf-8") as f:
        f.write(":".join(sorted(list(extra_packages))))

    # JNI Map
    with open(gen_dir / "jni_libs_map.json", "w", encoding="utf-8") as f:
        json.dump(jni_map, f, indent=2)

    print("=" * 60)
    print("\033[92mLockfile-Driven Expansion Complete.\033[0m")
    print(f"  Indexed Classpath entries: {len(classpath_entries)}")
    print(f"  Resource folders:          {len(res_dirs)}")
    print("=" * 60)

if __name__ == "__main__":
    main()
