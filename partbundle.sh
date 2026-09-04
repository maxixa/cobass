#!/usr/bin/env bash
set -euo pipefail

OUTPUT_FILE="context_synths_bundle.md"
GENERATED_DATE=$(date -u +"%a %b %d %H:%M:%S UTC %Y")

# Explicit list of files related to Hyperion Synth and Cobalt Drums
FILES=(
    # --- Cobalt Drums Plugin & Drum Voices ---
    "addons/synth-cobalt-drums/src/CobaltDrumsPlugin.cpp"
    "addons/synth-cobalt-drums/src/KickVoice.hpp"
    "addons/synth-cobalt-drums/src/SnareVoice.hpp"
    "addons/synth-cobalt-drums/src/HiHatVoice.hpp"
    "addons/synth-cobalt-drums/src/ClapVoice.hpp"
    "addons/synth-cobalt-drums/src/PercVoice.hpp"
    "addons/synth-cobalt-drums/src/TomVoice.hpp"

    # --- Hyperion Synth Plugin ---
    "addons/synth-hyperion/src/HyperionSynthPlugin.cpp"

    # --- Plugin ABI & Core Native Plugin Hosting ---
    "app/native/include/CobassPluginABI.h"
    "app/native/plugin/PluginDescriptor.hpp"
    "app/native/plugin/PluginInstance.hpp"
    "app/native/plugin/PluginLoader.hpp"
    "app/native/plugin/PluginChain.hpp"

    # --- Shared Core DSP Dependencies ---
    "app/native/dsp/SynthVoice.hpp"
    "app/native/dsp/SynthTrack.hpp"
    "app/native/dsp/PolyBlepOscillator.hpp"
    "app/native/dsp/ZdfFilter.hpp"
    "app/native/dsp/ADSR.hpp"
    "app/native/dsp/LFO.hpp"
    "app/native/dsp/Wavefolder.hpp"

    # --- Synth Tests & Benchmarks ---
    "tools/benchmark_hyperion_dance.py"
    "tools/test_hyperion_dsp_fixes.py"
    "tools/benchmark_variation_and_presets.py"
    "tools/build_addons.py"

    # --- Documentation & Design Plans ---
    "docs/synth-V2.md"
    "docs/plugin-synth-fx_doc.md"
    "plan/synth-host-plugin-v1.md"

    # --- Host UI & Preset Models (Android/Java) ---
    "app/src/com/maxica/cobass/plugin/PluginHostManager.java"
    "app/src/com/maxica/cobass/plugin/PatchVariationEngine.java"
    "app/src/com/maxica/cobass/ui/PluginUiDialog.java"
    "app/src/com/maxica/cobass/ui/PluginPresetDialog.java"
    "app/src/com/maxica/cobass/ui/SynthVisualizerView.java"
)

echo "==> Generating synth bundle: ${OUTPUT_FILE}..."

# Filter only existing files
EXISTING_FILES=()
for file in "${FILES[@]}"; do
    if [[ -f "$file" ]]; then
        EXISTING_FILES+=("$file")
    else
        echo "  [WARN] File not found (skipping): $file" >&2
    fi
done

TOTAL_COUNT=${#EXISTING_FILES[@]}

# Write Header & Manifest
cat <<EOF > "${OUTPUT_FILE}"
# Codebase Context Bundle: Hyperion Synth & Cobalt Drums

- **Generated on:** ${GENERATED_DATE}
- **Scope Profile:** \`hyperion-and-cobalt-drums\`
- **Total Files Included:** ${TOTAL_COUNT}
- **Root Directory:** \`.\`

---

## 1. Selected File Manifest
\`\`\`text
EOF

for file in "${EXISTING_FILES[@]}"; do
    echo "• $file" >> "${OUTPUT_FILE}"
done

cat <<EOF >> "${OUTPUT_FILE}"
\`\`\`

---

## 2. File Contents

EOF

# Function to detect language for code block formatting
get_lang() {
    case "$1" in
        *.cpp|*.hpp|*.h|*.c) echo "cpp" ;;
        *.java)             echo "java" ;;
        *.py)               echo "python" ;;
        *.md)               echo "markdown" ;;
        *.sh)               echo "bash" ;;
        *.json)             echo "json" ;;
        *.xml)              echo "xml" ;;
        *)                  echo "text" ;;
    esac
}

# Append each file's content
for file in "${EXISTING_FILES[@]}"; do
    LANG=$(get_lang "$file")
    echo "Processing: $file"

    cat <<EOF >> "${OUTPUT_FILE}"
### File: \`${file}\`

\`\`\`${LANG}
EOF
    cat "$file" >> "${OUTPUT_FILE}"
    echo "" >> "${OUTPUT_FILE}"
    echo '```' >> "${OUTPUT_FILE}"
    echo "" >> "${OUTPUT_FILE}"
    echo "---" >> "${OUTPUT_FILE}"
    echo "" >> "${OUTPUT_FILE}"
done

FILE_SIZE=$(du -h "${OUTPUT_FILE}" | cut -f1)
echo "==> Done! Successfully bundled ${TOTAL_COUNT} files into '${OUTPUT_FILE}' (${FILE_SIZE})."