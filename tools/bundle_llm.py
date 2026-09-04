#!/usr/bin/env python3
"""
Cobass Intelligent LLM Context Bundler & Token Optimizer
Generates scoped, token-budgeted bundles for LLM prompts without context overflow.

Examples:
    # 1. Extract ONLY signatures/declarations (no implementation bodies):
    python3 tools/bundle_llm.py --signatures-only --out interface_skeleton.md

    # 2. Extract signatures and exclude specific subfolders:
    python3 tools/bundle_llm.py --signatures-only --exclude-folders app/src/com/maxica/cobass/ui/

    # 3. Focus on DSP audio engine (C++ only, signatures only):
    python3 tools/bundle_llm.py --scope dsp --signatures-only --out context_dsp_signatures.md
"""
import argparse
import datetime
import os
import re
import sys
from pathlib import Path
from typing import List, Set

# Universal directory exclusions (never bundle)
GLOBAL_EXCLUDE_DIRS = {
    "out", "backups", "build_tmp", ".git", "__pycache__",
    "libs/cache", "libs/downloaded", "libs/exploded", "app/lib"
}

ALLOWED_EXTENSIONS = {
    ".java", ".cpp", ".h", ".hpp", ".py", ".toml", ".json", ".xml", ".sh", ".md"
}

EXCLUDED_FILES = {
    "beatforge_llm_bundle.txt", "debug.keystore", "llm_context.md",
    "resolved.lock.json", "classpath.txt", "res_dirs.txt", "extra_packages.txt"
}

SCOPE_DEFINITIONS = {
    "dsp": [
        "app/native/dsp/",
        "app/native/AudioEngine.",
        "app/native/LockFreeQueue.hpp",
        "app/native/include/",
        "app/native/jni_bridge.cpp"
    ],
    "transform": [
        "app/native/sequencer/MusicTheory.hpp",
        "app/native/sequencer/NoteTransformEngine.hpp",
        "app/src/com/maxica/cobass/sequencer/NoteTransform",
        "app/src/com/maxica/cobass/sequencer/MidiTransform",
        "app/src/com/maxica/cobass/model/Transform",
        "app/src/com/maxica/cobass/model/MusicalScale.java",
        "app/src/com/maxica/cobass/ui/MidiTransformStudioDialog.java"
    ],
    "ui": [
        "app/src/com/maxica/cobass/ui/",
        "app/res/layout/",
        "app/res/values/colors.xml",
        "app/res/values/strings.xml",
        "app/src/com/maxica/cobass/model/"
    ],
    "sequencer": [
        "app/native/sequencer/",
        "app/src/com/maxica/cobass/sequencer/",
        "app/src/com/maxica/cobass/model/",
        "app/src/com/maxica/cobass/ui/Arranger",
        "app/src/com/maxica/cobass/ui/PianoRoll",
        "app/src/com/maxica/cobass/ui/StepMatrix",
        "app/src/com/maxica/cobass/ui/StepSequencer"
    ],
    "plugins": [
        "addons/",
        "app/native/include/CobassPluginABI.h",
        "app/native/plugin/",
        "app/src/com/maxica/cobass/plugin/",
        "app/src/com/maxica/cobass/ui/Plugin",
        "app/src/com/maxica/cobass/ui/VariationStudio"
    ],
    "wave": [
        "app/src/com/maxica/cobass/ui/WaveEditor",
        "app/native/dsp/AudioTrack.hpp",
        "app/native/export/WavExporter.hpp"
    ],
    "build": [
        "tools/doctor.py",
        "tools/module_check.py",
        "tools/native_build.py",
        "tools/build_apk.py",
        "tools/build_addons.py",
        "tools/lib_resolver.py",
        "build.sh",
        "buildfull.sh",
        "config/deps.toml",
        "app/AndroidManifest.xml",
        "NO_GRADLE_POLICY.md"
    ]
}

def is_globally_excluded(path: Path) -> bool:
    for part in path.parts:
        if part in GLOBAL_EXCLUDE_DIRS:
            return True
    path_str = str(path).replace("\\", "/")
    for exc in GLOBAL_EXCLUDE_DIRS:
        if exc in path_str:
            return True
    if path.name in EXCLUDED_FILES or path.suffix not in ALLOWED_EXTENSIONS:
        return True
    return False

def matches_scope(rel_str: str, scope: str) -> bool:
    if scope == "all":
        return True
    patterns = SCOPE_DEFINITIONS.get(scope, [])
    return any(p in rel_str for p in patterns)

def extract_signatures(content: str, suffix: str) -> str:
    """
    Strips implementation blocks and internal bodies while retaining package declarations,
    imports, annotations, type definitions, interface definitions, and method/function signatures.
    """
    if suffix in [".java", ".cpp", ".hpp", ".h"]:
        # Strip comments first
        content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
        content = re.sub(r'//.*$', '', content, flags=re.MULTILINE)
        
        # Balance braces and replace code blocks with standard abstract declarations
        lines = content.splitlines()
        skeleton = []
        brace_depth = 0
        in_signature = False

        for line in lines:
            stripped = line.strip()

            # Preserve headers, imports, annotations, package names, and interface statements
            if brace_depth == 0 and (
                stripped.startswith("package ") or 
                stripped.startswith("import ") or 
                stripped.startswith("#include") or 
                stripped.startswith("#define") or 
                stripped.startswith("#pragma") or
                stripped.startswith("@")
            ):
                skeleton.append(line)
                continue

            # Process brace structures
            open_braces = line.count('{')
            close_braces = line.count('}')

            if open_braces > 0:
                # Class or Interface top declaration level
                if brace_depth == 0:
                    skeleton.append(line)
                # Method declaration scope - collapse body to stub
                elif brace_depth == 1 and not stripped.startswith("static {"):
                    header = line.split('{')[0].strip()
                    if header:
                        indent = " " * (line.find(line.lstrip()) if line.lstrip() else 4)
                        skeleton.append(f"{indent}{header} {{ /* ... */ }}")
                brace_depth += open_braces - close_braces
                continue

            if brace_depth > 0:
                brace_depth += open_braces - close_braces
                # Keep end-of-class braces
                if brace_depth == 0:
                    skeleton.append("}")
                continue

            if stripped and brace_depth == 0:
                skeleton.append(line)

        return "\n".join(skeleton)

    elif suffix == ".py":
        lines = content.splitlines()
        skeleton = []
        for line in lines:
            stripped = line.strip()
            # Retain imports, class definitions, function signatures, and docstrings
            if (
                stripped.startswith("import ") or 
                stripped.startswith("from ") or 
                stripped.startswith("class ") or 
                stripped.startswith("def ") or
                stripped.startswith("@") or
                stripped.startswith('"""') or
                stripped.startswith("#!")
            ):
                if stripped.startswith("def "):
                    indent = " " * (len(line) - len(line.lstrip()))
                    skeleton.append(f"{line.split(':')[0]}:")
                    skeleton.append(f"{indent}    ...")
                else:
                    skeleton.append(line)
        return "\n".join(skeleton)

    return content

def strip_code_comments(content: str, suffix: str) -> str:
    if suffix in [".java", ".cpp", ".hpp", ".h"]:
        content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)
        lines = [re.sub(r'//.*$', '', line) for line in content.splitlines()]
        content = "\n".join(lines)
    elif suffix in [".py", ".sh"]:
        lines = [line for line in content.splitlines() if not (line.strip().startswith("#") and not line.startswith("#!"))]
        content = "\n".join(lines)
    return content

def clean_whitespace(content: str) -> str:
    content = content.replace("\r\n", "\n")
    lines = [line.rstrip() for line in content.split("\n")]
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

def collect_files(root: Path, args) -> List[Path]:
    files = []
    
    if args.files:
        for f in args.files:
            p = (root / f).resolve()
            if p.is_file() and not is_globally_excluded(p):
                files.append(p)
        return sorted(files)

    # Convert excluded paths to normal slash strings
    exclude_folders = [f.replace("\\", "/").rstrip("/") for f in (args.exclude_folders or [])]

    for p in root.rglob("*"):
        if not p.is_file() or is_globally_excluded(p):
            continue

        rel_str = str(p.relative_to(root)).replace("\\", "/")

        # Custom directory/folder exclusions
        if any(rel_str.startswith(folder + "/") or folder == rel_str for folder in exclude_folders):
            continue

        # Scope filter
        if args.scope != "all" and not matches_scope(rel_str, args.scope):
            continue

        # Sub-system exclusions
        if args.exclude_plans and rel_str.startswith("plan/"):
            continue
        if args.exclude_tests and (rel_str.startswith("tools/test_") or rel_str.startswith("tools/benchmark_") or rel_str.startswith("tools/verify_")):
            continue
        if args.exclude_addons and rel_str.startswith("addons/"):
            continue
        if args.exclude_layouts and rel_str.startswith("app/res/layout/"):
            continue
        if args.exclude_docs and (rel_str.startswith("docs/") or rel_str.endswith(".md")):
            continue

        files.append(p)

    return sorted(files)

def generate_bundle(root: Path, files: List[Path], out_file: Path, args):
    mode_str = "SIGNATURES ONLY" if args.signatures_only else "FULL CODE"
    print(f"[*] Packaging {len(files)} files into bundle (Scope: {args.scope.upper()} | Mode: {mode_str})...")

    total_chars = 0
    total_lines = 0
    file_stats = []

    timestamp = datetime.datetime.now().strftime("%a %b %d %H:%M:%S %Z %Y")

    with open(out_file, "w", encoding="utf-8") as out:
        out.write("# Codebase Context Bundle\n\n")
        out.write(f"- **Generated on:** {timestamp}\n")
        out.write(f"- **Scope Profile:** `{args.scope}`\n")
        out.write(f"- **Extraction Mode:** `{mode_str}`\n")
        out.write(f"- **Total Files:** {len(files)}\n")
        out.write(f"- **Root Directory:** `.`\n\n")
        out.write("---\n\n")

        out.write("## 1. Selected File Manifest\n```text\n")
        for f in files:
            rel = f.relative_to(root)
            out.write(f"• {rel}\n")
        out.write("```\n\n---\n\n")

        if args.tree_only:
            print(f"\033[92m[✓] Tree-only manifest created: {out_file.name}\033[0m")
            return

        out.write("## 2. File Contents\n\n")
        for idx, f in enumerate(files, 1):
            rel = f.relative_to(root)
            suffix = f.suffix.lower()

            try:
                raw_text = f.read_text(encoding="utf-8", errors="replace")
                
                if args.signatures_only:
                    raw_text = extract_signatures(raw_text, suffix)
                elif args.compact:
                    raw_text = strip_code_comments(raw_text, suffix)

                cleaned_text = clean_whitespace(raw_text)

                line_count = len(cleaned_text.splitlines())
                char_count = len(cleaned_text)
                est_tokens = int(char_count / 3.8)

                total_lines += line_count
                total_chars += char_count
                file_stats.append((rel, est_tokens))

                lang = suffix.lstrip(".")
                if lang in ["hpp", "h"]: lang = "cpp"
                elif lang in ["sh"]: lang = "bash"

                out.write(f"### File: `{rel}`\n\n")
                out.write(f"```{lang}\n")
                out.write(cleaned_text)
                out.write("\n```\n\n---\n\n")
            except Exception as e:
                print(f"  [!] Skipped {rel}: {e}")

    total_est_tokens = int(total_chars / 3.8)
    kb_size = out_file.stat().st_size / 1024

    print("=" * 65)
    print("\033[92mLLM CONTEXT BUNDLE GENERATED SUCCESSFULLY\033[0m")
    print(f"Output File:        {out_file.resolve()}")
    print(f"Bundle Size:        {kb_size:.1f} KB")
    print(f"Total Lines:        {total_lines:,}")
    print(f"Estimated Tokens:   ~{total_est_tokens:,} tokens")
    print("=" * 65)

def main():
    parser = argparse.ArgumentParser(description="Cobass Context Token Optimizer & Bundler")
    parser.add_argument("--scope", default="all", choices=list(SCOPE_DEFINITIONS.keys()) + ["all"],
                        help="Target specific subsystem scope to save tokens")
    parser.add_argument("--out", default="llm_context.md", help="Output Markdown bundle path")
    parser.add_argument("--files", nargs="+", help="Explicit list of files to bundle exclusively")
    parser.add_argument("--signatures-only", action="store_true", 
                        help="Extract headers, imports, interfaces, signatures and drop implementation bodies")
    parser.add_argument("--exclude-folders", nargs="+", help="Exclude specific folders or paths (e.g. app/src/com/maxica/cobass/ui)")
    parser.add_argument("--compact", action="store_true", help="Strip comments and collapse whitespace")
    parser.add_argument("--tree-only", action="store_true", help="Generate only the file list and structure")
    parser.add_argument("--exclude-plans", action="store_true", help="Omit plan/*.md files")
    parser.add_argument("--exclude-tests", action="store_true", help="Omit test files")
    parser.add_argument("--exclude-addons", action="store_true", help="Omit addons/ plugins")
    parser.add_argument("--exclude-layouts", action="store_true", help="Omit XML layouts")
    parser.add_argument("--exclude-docs", action="store_true", help="Omit docs/ and markdown files")

    args = parser.parse_args()
    root = Path.cwd().resolve()
    out_path = Path(args.out).resolve()

    files = collect_files(root, args)
    if not files:
        print("\033[91mNo files matched the selected criteria.\033[0m")
        sys.exit(1)

    generate_bundle(root, files, out_path, args)

if __name__ == "__main__":
    main()
