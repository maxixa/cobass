#!/usr/bin/env bash
# ==============================================================================
# Cobass DAW - Fix Missing java.io.File Import in MidiTransformStudioDialog
# ==============================================================================
set -euo pipefail

echo "======================================================================"
echo "    FIXING MISSING IMPORT IN MIDITRANSFORMSTUDIODIALOG.JAVA           "
echo "======================================================================"

# ------------------------------------------------------------------------------
# 1. Add missing java.io.File import to MidiTransformStudioDialog.java
# ------------------------------------------------------------------------------
echo "==> [1/2] Adding import java.io.File to MidiTransformStudioDialog.java..."
python3 - << 'PYEOF'
from pathlib import Path

file_path = Path("app/src/com/maxica/cobass/ui/MidiTransformStudioDialog.java")
content = file_path.read_text(encoding="utf-8")

if "import java.io.File;" not in content:
    target = "import com.maxica.cobass.sequencer.MidiTransformEngine;\n"
    replacement = "import com.maxica.cobass.sequencer.MidiTransformEngine;\nimport java.io.File;\n"
    content = content.replace(target, replacement)
    file_path.write_text(content, encoding="utf-8")
    print("  [+] Added import java.io.File;")
else:
    print("  [*] Import already present.")
PYEOF

# ------------------------------------------------------------------------------
# 2. Run Validation & Build Release APK
# ------------------------------------------------------------------------------
echo "==> [2/2] Running Validation Suites & Building APK..."

python3 tools/module_check.py
python3 tools/test_music_theory_engine.py
python3 tools/test_rhythmic_engine.py
python3 tools/test_melodic_engine.py
python3 tools/test_pipeline_engine.py
python3 tools/test_transform_glue.py
python3 tools/test_studio_ui_integration.py
python3 tools/test_enhancement_phase1.py
python3 tools/test_enhancement_phase2.py
python3 tools/test_enhancement_phase3.py
python3 tools/test_enhancement_phase4.py
python3 tools/test_enhancement_phase5.py
python3 tools/test_enhancement_phase6.py

./build.sh

echo "======================================================================"
echo -e "\033[92m[✓] COMPILATION SUCCEEDED & RELEASE APK BUILT SUCCESSFULLY!\033[0m"
echo "======================================================================"