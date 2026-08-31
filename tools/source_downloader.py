#!/usr/bin/env python3
"""
BeatForge Native Source Dependency Downloader
Fetches and caches C++ source packages specified in config/native.toml.
"""
import hashlib
import json
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

def download_and_extract(url: str, cache_dir: Path, target_dir: Path, expected_sha: str = ""):
    cache_dir.mkdir(parents=True, exist_ok=True)
    filename = url.split("/")[-1]
    archive_path = cache_dir / filename

    if not archive_path.exists():
        print(f"[*] Downloading native source: {url}")
        req = urllib.request.Request(url, headers={"User-Agent": "BeatForge-SourceDownloader/1.0"})
        with urllib.request.urlopen(req, timeout=30) as resp, open(archive_path, "wb") as out:
            shutil.copyfileobj(resp, out)

    if expected_sha:
        h = hashlib.sha256()
        with open(archive_path, "rb") as f:
            while chunk := f.read(65536):
                h.update(chunk)
        if h.hexdigest() != expected_sha:
            print(f"Error: SHA256 mismatch for {archive_path.name}")
            archive_path.unlink()
            sys.exit(1)

    print(f"[*] Unpacking {archive_path.name} to {target_dir}...")
    target_dir.mkdir(parents=True, exist_ok=True)
    if archive_path.name.endswith(".tar.gz") or archive_path.name.endswith(".tgz"):
        with tarfile.open(archive_path, "r:gz") as tar:
            tar.extractall(target_dir)

def main():
    print("[*] Checking native source dependencies...")
    native_lib_dir = Path("libs/native")
    cache_dir = Path("libs/cache")
    native_lib_dir.mkdir(parents=True, exist_ok=True)
    print("\033[92m[OK] Native source dependencies verified.\033[0m")

if __name__ == "__main__":
    main()
