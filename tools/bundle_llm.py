#!/usr/bin/env python3
"""
BeatForge LLM Code Bundler
Compresses and packages source code into a token-optimized text bundle for LLMs.
"""
import argparse
import os
import re
import sys
from pathlib import Path

EXCLUDED_DIRS = {
    "out", "backups", "build_tmp", ".git", "__pycache__",
    "libs/cache", "libs/downloaded", "libs/exploded", "app/lib"
}

ALLOWED_EXTENSIONS = {
    ".java", ".cpp", ".h", ".py", ".toml", ".json", ".xml", ".sh", ".md"
}

EXCLUDED_FILES = {
    "beatforge_llm_bundle.txt", "debug.keystore"
}

def is_excluded(path: Path) -> bool:
    for part in path.parts:
        if part in EXCLUDED_DIRS:
            return True
    path_str = str(path).replace("\\", "/")
    for exc in EXCLUDED_DIRS:
        if exc in path_str:
            return True
    if path.name in EXCLUDED_FILES or path.suffix not in ALLOWED_EXTENSIONS:
        return True
    return False

def clean_content(content: str, compact: bool = True) -> str:
    # Normalize line endings
    content = content.replace("\r\n", "\n")
    
    if compact:
        # Strip trailing whitespaces on each line
        lines = [line.rstrip() for line in content.split("\n")]
        # Collapse 3+ consecutive empty lines into 1
        cleaned = []
        consecutive_blanks = 0
        for line in lines:
            if not line.strip():
                consecutive_blanks += 1
                if consecutive_blanks <= 1:
                    cleaned.append("")
            else:
                consecutive_blanks = 0
                cleaned.append(line)
        return "\n".join(cleaned).strip()
    return content.strip()

def collect_files(root: Path) -> list[Path]:
    files = []
    for p in root.rglob("*"):
        if p.is_file() and not is_excluded(p):
            files.append(p)
    return sorted(files)

def generate_bundle(output_file: Path, compact: bool = True):
    root = Path.cwd()
    files = collect_files(root)

    print(f"[*] Discovered {len(files)} source files for bundling...")

    total_chars = 0
    total_lines = 0

    with open(output_file, "w", encoding="utf-8") as out:
        # Header & File Manifest Tree
        out.write("=" * 80 + "\n")
        out.write("PROJECT: BeatForge (com.maxixa.beatforge)\n")
        out.write("ARCHITECTURE: No-Gradle Modular Android DAW (C++17 AAudio Engine)\n")
        out.write(f"TOTAL SOURCE FILES: {len(files)}\n")
        out.write("=" * 80 + "\n\n")

        out.write("--- FILE MANIFEST TREE ---\n")
        for f in files:
            rel = f.relative_to(root)
            out.write(f"• {rel}\n")
        out.write("--- END OF MANIFEST ---\n\n")

        # Code Contents
        for idx, f in enumerate(files, 1):
            rel = f.relative_to(root)
            try:
                raw_text = f.read_text(encoding="utf-8", errors="replace")
                cleaned_text = clean_content(raw_text, compact)
                
                line_count = len(cleaned_text.splitlines())
                char_count = len(cleaned_text)
                total_lines += line_count
                total_chars += char_count

                out.write("=" * 80 + "\n")
                out.write(f"FILE [{idx}/{len(files)}]: {rel} ({line_count} lines, {char_count} chars)\n")
                out.write("=" * 80 + "\n")
                out.write(cleaned_text + "\n\n")
            except Exception as e:
                print(f"  [!] Skipped {rel}: {e}")

    est_tokens = int(total_chars / 3.8) # Average ratio for code tokens
    kb_size = output_file.stat().st_size / 1024

    print("=" * 65)
    print("\033[92mLLM BUNDLE GENERATION COMPLETE\033[0m")
    print(f"Output File:       {output_file.resolve()}")
    print(f"Bundle Size:       {kb_size:.1f} KB")
    print(f"Total Lines:       {total_lines:,}")
    print(f"Total Characters:  {total_chars:,}")
    print(f"Estimated Tokens:  ~{est_tokens:,} tokens")
    print("=" * 65)

def main():
    parser = argparse.ArgumentParser(description="BeatForge LLM Source Bundler")
    parser.add_argument("--out", default="beatforge_llm_bundle.txt", help="Output text bundle path")
    parser.add_argument("--no-compact", action="store_true", help="Preserve raw whitespace/blank lines")
    args = parser.parse_args()

    generate_bundle(Path(args.out), compact=not args.no_compact)

if __name__ == "__main__":
    main()
