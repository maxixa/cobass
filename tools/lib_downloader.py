#!/usr/bin/env python3
"""
BeatForge Deterministic Library Downloader & Cache Manager
Skips downloading if artifacts already exist in libs/downloaded or libs/cache.
Verifies SHA-256 checksums on newly fetched or unverified files.
"""
import argparse
import hashlib
import json
import shutil
import sys
import urllib.request
from pathlib import Path

def calculate_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def download_file(url: str, dest: Path) -> bool:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "BeatForge-LibDownloader/1.0"}
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp, open(dest, "wb") as out:
            shutil.copyfileobj(resp, out)
        return True
    except Exception:
        if dest.exists():
            dest.unlink()
        return False

def main():
    parser = argparse.ArgumentParser(description="BeatForge Library Downloader")
    parser.add_argument("--lock", default="libs/resolved.lock.json", help="Path to resolved.lock.json")
    parser.add_argument("--cache", default="libs/cache", help="Path to cache directory")
    parser.add_argument("--out", default="libs/downloaded", help="Path to downloaded output directory")
    parser.add_argument("--offline", action="store_true", help="Operate in offline mode using cache only")
    parser.add_argument("--force", action="store_true", help="Force redownload even if exists")
    args = parser.parse_args()

    lock_path = Path(args.lock)
    cache_dir = Path(args.cache)
    out_dir = Path(args.out)

    if not lock_path.exists():
        print(f"\033[91mError: Lockfile {lock_path} not found. Run lib_resolver.py first.\033[0m")
        sys.exit(1)

    cache_dir.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    with open(lock_path, "r", encoding="utf-8") as f:
        lock_data = json.load(f)

    artifacts = lock_data.get("artifacts", [])
    total = len(artifacts)
    print(f"[*] Checking {total} artifacts from {lock_path}...")

    skipped_count = 0
    downloaded_count = 0
    updated_lock = False

    for idx, item in enumerate(artifacts, 1):
        artifact_id = item["artifact"]
        version = item["version"]
        packaging = item["packaging"]
        url = item["url"]
        expected_sha = item.get("sha256", "")
        filename = f"{artifact_id}-{version}.{packaging}"

        cache_file = cache_dir / filename
        target_file = out_dir / filename
        prefix = f"[{idx}/{total}] {filename}"

        # 1. If target file already exists and not forced
        if not args.force and target_file.exists() and target_file.stat().st_size > 0:
            if not expected_sha:
                computed_sha = calculate_sha256(target_file)
                item["sha256"] = computed_sha
                updated_lock = True
            print(f"  \033[92m[EXISTS - SKIP]\033[0m {prefix}")
            skipped_count += 1
            # Ensure cache also has it
            if not cache_file.exists():
                shutil.copy2(target_file, cache_file)
            continue

        # 2. If cached file exists
        if not args.force and cache_file.exists() and cache_file.stat().st_size > 0:
            computed_sha = calculate_sha256(cache_file)
            if not expected_sha or computed_sha == expected_sha:
                shutil.copy2(cache_file, target_file)
                if not expected_sha:
                    item["sha256"] = computed_sha
                    updated_lock = True
                print(f"  \033[94m[CACHE HIT - SKIP]\033[0m {prefix}")
                skipped_count += 1
                continue

        # 3. Offline check
        if args.offline:
            print(f"  \033[91m[OFFLINE MISS]\033[0m {prefix} - Missing in cache")
            continue

        # 4. Download missing library
        print(f"  \033[93m[DOWNLOADING]\033[0m {prefix} -> {url}")
        if download_file(url, cache_file):
            computed_sha = calculate_sha256(cache_file)
            if expected_sha and computed_sha != expected_sha:
                print(f"  \033[91m[CHECKSUM MISMATCH]\033[0m {filename}: expected {expected_sha}, got {computed_sha}")
                cache_file.unlink()
                sys.exit(1)

            item["sha256"] = computed_sha
            updated_lock = True
            shutil.copy2(cache_file, target_file)
            print(f"  \033[92m[VERIFIED & SAVED]\033[0m {prefix} (SHA-256: {computed_sha[:8]}...)")
            downloaded_count += 1
        else:
            print(f"  \033[91m[FAILED]\033[0m {prefix} could not be downloaded from {url}")
            sys.exit(1)

    if updated_lock:
        with open(lock_path, "w", encoding="utf-8") as f:
            json.dump(lock_data, f, indent=2)

    print("=" * 60)
    print(f"\033[92mStatus: {skipped_count} skipped (already available), {downloaded_count} downloaded.\033[0m")
    print(f"Output Directory: {out_dir}")
    print("=" * 60)

if __name__ == "__main__":
    main()
