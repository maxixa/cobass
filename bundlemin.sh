#!/usr/bin/env bash

# bundle.sh - Android (Kotlin/Java/C/C++) Project Signature Extractor
# Usage: ./bundle.sh [output_file] [--exclude directory_name]

OUTPUT_FILE="project_signatures.txt"
EXCLUDE_DIRS=("build" ".gradle" ".git" "libs" "out" "androidTest" "captures" ".cxx" "cmakeBuild" "obj")

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --exclude|-e)
      EXCLUDE_DIRS+=("$2")
      shift 2
      ;;
    *)
      OUTPUT_FILE="$1"
      shift
      ;;
  esac
done

# Build find exclusion arguments
FIND_EXCLUDES=()
for dir in "${EXCLUDE_DIRS[@]}"; do
  FIND_EXCLUDES+=( -path "*/$dir/*" -o -path "*/$dir" -o )
done

echo "=================================================" > "$OUTPUT_FILE"
echo "  ANDROID PROJECT SIGNATURE DUMP (KT/JAVA/C/C++)" >> "$OUTPUT_FILE"
echo "  Excluded directories: ${EXCLUDE_DIRS[*]}" >> "$OUTPUT_FILE"
echo "=================================================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Process Kotlin Files
strip_kotlin() {
  sed -E \
    -e '/^[[:space:]]*\/\//d' \
    -e '/^[[:space:]]*\/\*/,/\*\//d' \
    -e '/^[[:space:]]*fun[[:space:]]+.*\{/s/\{.*/ = .../g' \
    -e '/^[[:space:]]*init[[:space:]]*\{/,/^[[:space:]]*\}/d' \
    -e '/^[[:space:]]*constructor[[:space:]]*\(.*\)[[:space:]]*\{/,/^[[:space:]]*\}/d' \
    "$1" | grep -v '^[[:space:]]*$'
}

# Process Java Files
strip_java() {
  sed -E \
    -e '/^[[:space:]]*\/\//d' \
    -e '/^[[:space:]]*\/\*/,/\*\//d' \
    -e '/(public|protected|private|static|final|\<[A-Z]\>)+[[:space:]]+.*\(.*\)[[:space:]]*\{/s/\{.*/;/g' \
    "$1" | grep -v '^[[:space:]]*$'
}

# Process C/C++ Files (.c, .cpp, .cc, .cxx)
strip_c_cpp() {
  sed -E \
    -e '/^[[:space:]]*\/\//d' \
    -e '/^[[:space:]]*\/\*/,/\*\//d' \
    -e '/^[[:space:]]*[A-Za-z0-9_:\*<>[[:space:]]]+\(.*\)[[:space:]]*\{/s/\{.*/;/g' \
    "$1" | grep -v '^[[:space:]]*$'
}

# Process Header Files (.h, .hpp) - Keep declarations, strip comments/blank lines
strip_headers() {
  sed -E \
    -e '/^[[:space:]]*\/\//d' \
    -e '/^[[:space:]]*\/\*/,/\*\//d' \
    "$1" | grep -v '^[[:space:]]*$'
}

export -f strip_kotlin strip_java strip_c_cpp strip_headers

# Find and process files
find . \( "${FIND_EXCLUDES[@]}" -false \) -prune -o \
  \( -name "*.kt" -o -name "*.java" -o -name "*.c" -o -name "*.cpp" -o -name "*.cc" -o -name "*.cxx" -o -name "*.h" -o -name "*.hpp" \) \
  -print | while read -r file; do
  
  echo " Processing: $file"
  
  echo "=================================================" >> "$OUTPUT_FILE"
  echo "FILE: $file" >> "$OUTPUT_FILE"
  echo "=================================================" >> "$OUTPUT_FILE"
  
  case "$file" in
    *.kt)
      strip_kotlin "$file" >> "$OUTPUT_FILE"
      ;;
    *.java)
      strip_java "$file" >> "$OUTPUT_FILE"
      ;;
    *.c|*.cpp|*.cc|*.cxx)
      strip_c_cpp "$file" >> "$OUTPUT_FILE"
      ;;
    *.h|*.hpp)
      strip_headers "$file" >> "$OUTPUT_FILE"
      ;;
  esac
  
  echo -e "\n\n" >> "$OUTPUT_FILE"
done

echo "Done! Signatures bundled into: $OUTPUT_FILE"
