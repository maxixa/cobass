#!/usr/bin/env bash
# ==============================================================================
# bundle.sh - Bundles a codebase into a single structured prompt for LLMs.
# Usage: ./bundle.sh [-o output_file.md] [-d root_dir]
# ==============================================================================

set -euo pipefail

OUTPUT_FILE="llm_context.md"
ROOT_DIR="."

# Parse optional arguments
while getopts "o:d:h" opt; do
  case ${opt} in
    o ) OUTPUT_FILE="$OPTARG" ;;
    d ) ROOT_DIR="$OPTARG" ;;
    h )
      echo "Usage: ./bundle.sh [-o output_file] [-d root_directory]"
      exit 0
      ;;
    \? )
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
  esac
done

# Directories to ignore
EXCLUDE_DIRS=(
  ".git"
  "out"
  "libs"
  "build"
  "obj"
  "bin"
  "dex"
  "compiled_res"
  "apk_root"
  ".idea"
  ".vscode"
  ".gradle"
  "node_modules"
  "dist"
  "__pycache__"
)

# File extensions to ignore (binaries, compiled artifacts, media, archives)
EXCLUDE_EXTS=(
  "so" "a" "o" "obj" "dex" "class" "jar" "apk" "keystore"
  "png" "jpg" "jpeg" "gif" "ico" "webp" "svg"
  "wav" "mp3" "ogg" "flac" "mid" "midi"
  "zip" "tar" "gz" "7z" "rar"
  "DS_Store" "pdf" "exe" "dll"
)

# Helper: Map file extension to markdown language identifier
get_syntax_highlight() {
  local filename="$1"
  local ext="${filename##*.}"
  case "$ext" in
    c|h) echo "c" ;;
    cpp|hpp|cc|cxx) echo "cpp" ;;
    java) echo "java" ;;
    kt|kts) echo "kotlin" ;;
    xml) echo "xml" ;;
    sh|bash) echo "bash" ;;
    mk|Makefile|makefile) echo "makefile" ;;
    json) echo "json" ;;
    md) echo "markdown" ;;
    txt) echo "text" ;;
    py) echo "python" ;;
    *) echo "" ;;
  esac
}

# Helper: Check if directory is in exclusion list
is_excluded_dir() {
  local dir="$1"
  for exc in "${EXCLUDE_DIRS[@]}"; do
    if [[ "$dir" == *"/$exc"* ]] || [[ "$dir" == "$exc" ]] || [[ "$dir" == "./$exc"* ]]; then
      return 0
    fi
  done
  return 1
}

# Helper: Check if file extension is in exclusion list
is_excluded_ext() {
  local file="$1"
  local ext="${file##*.}"
  for exc in "${EXCLUDE_EXTS[@]}"; do
    if [[ "$ext" == "$exc" ]] || [[ "$file" == *".$exc" ]]; then
      return 0
    fi
  done
  return 1
}

echo "==> Bundling codebase from: ${ROOT_DIR}"
echo "==> Output target: ${OUTPUT_FILE}"

# Remove old output file if it exists
rm -f "${OUTPUT_FILE}"

# Start writing header
{
  echo "# Codebase Context Bundle"
  echo ""
  echo "- **Generated on:** $(date)"
  echo "- **Root Directory:** \`${ROOT_DIR}\`"
  echo ""
  echo "---"
  echo ""
  echo "## 1. Project Directory Structure"
  echo '```text'
} >> "${OUTPUT_FILE}"

# Generate Directory Tree (fallback to find if tree is not installed)
if command -v tree >/dev/null 2>&1; then
  # Build tree ignore pattern
  TREE_IGNORE=$(IFS="|"; echo "${EXCLUDE_DIRS[*]}")
  tree -a -I "${TREE_IGNORE}" "${ROOT_DIR}" >> "${OUTPUT_FILE}"
else
  find "${ROOT_DIR}" -not -path '*/.*' | sort | sed -e 's/[^-][^\/]*\// |/g' -e 's/|\([^ ]\)/|-- \1/' >> "${OUTPUT_FILE}"
fi

{
  echo '```'
  echo ""
  echo "---"
  echo ""
  echo "## 2. File Contents"
  echo ""
} >> "${OUTPUT_FILE}"

# Iterate and append text files
FILE_COUNT=0
TOTAL_LINES=0

# Use find to list all files safely
while IFS= read -r -d '' file; do
  rel_path="${file#./}"

  # Check if in excluded directory
  if is_excluded_dir "$(dirname "$file")"; then
    continue
  fi

  # Check if excluded extension
  if is_excluded_ext "$file"; then
    continue
  fi

  # Skip self output
  if [[ "$rel_path" == "$OUTPUT_FILE" ]] || [[ "$(basename "$file")" == "bundle.sh" ]]; then
    continue
  fi

  # Double-check: ensure file is text and not binary
  if command -v file >/dev/null 2>&1; then
    if ! file --mime "$file" | grep -q "text/"; then
      continue
    fi
  fi

  LANG_TAG=$(get_syntax_highlight "$file")
  LINE_COUNT=$(wc -l < "$file" || echo "0")
  TOTAL_LINES=$((TOTAL_LINES + LINE_COUNT))
  FILE_COUNT=$((FILE_COUNT + 1))

  echo "  [+] Bundling: ${rel_path} (${LINE_COUNT} lines)"

  {
    echo "### File: \`${rel_path}\`"
    echo ""
    echo '```'"${LANG_TAG}"
    cat "$file"
    echo ""
    echo '```'
    echo ""
    echo "---"
    echo ""
  } >> "${OUTPUT_FILE}"

done < <(find "${ROOT_DIR}" -type f -print0)

# Summary
BUNDLE_SIZE=$(du -h "${OUTPUT_FILE}" | cut -f1)

echo ""
echo "=========================================================="
echo " BUNDLE CREATED SUCCESSFULLY: ${OUTPUT_FILE}"
echo " Total Files Bundled: ${FILE_COUNT}"
echo " Total Lines of Code: ${TOTAL_LINES}"
echo " Bundle File Size:    ${BUNDLE_SIZE}"
echo "=========================================================="