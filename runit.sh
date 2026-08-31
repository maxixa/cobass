
#!/usr/bin/env bash
# ==============================================================================
# Cobass DAW - Git & GitHub Repository Setup Script
# ==============================================================================
set -euo pipefail

echo "======================================================================"
echo "==> [1/4] Generating Production .gitignore"
echo "======================================================================"

cat << 'EOF' > .gitignore
# Build Artifacts & Packaging Outputs
out/
build_tmp/
build/
*.apk
*.idsig
*.dex
*.class
*.flat
*.zip

# Native Build Outputs
app/lib/arm64-v8a/
app/lib/armeabi-v7a/
app/lib/x86_64/
*.so
*.o
*.a

# Dependency Caches (Managed via deps.toml & resolved.lock.json)
libs/downloaded/
libs/exploded/
libs/cache/
libs/native/

# Keystores & Security
config/debug.keystore
*.keystore
*.jks

# Backup Archives & LLM Context Dumps
backups/
*.tar.gz
*.zip
beatforge_llm_bundle.txt
llm_context.md

# IDE & OS Metadata
.idea/
.vscode/
*.swp
*.swo
*~
.DS_Store
Thumbs.db
__pycache__/
*.pyc

# Gradle Prohibition (Enforces NO_GRADLE_POLICY)
.gradle/
gradlew
gradlew.bat
build.gradle
settings.gradle
EOF

echo "  [✓] .gitignore created."

echo ""
echo "======================================================================"
echo "==> [2/4] Generating Project README.md"
echo "======================================================================"

cat << 'EOF' > README.md
# Cobass — Low-Latency Modular Android DAW

**Cobass** (`com.maxica.cobass`) is a professional, Cubase-inspired digital audio workstation for Android built entirely with **C++20**, **AAudio**, and a **pure standalone CLI toolchain (No Gradle)**.

---

## Key Architecture & Features

- **No-Gradle Architecture**: Fully deterministic builds powered by Python orchestrators, `aapt2`, `d8`, `javac`, and `clang++`.
- **Low-Latency Engine**: Native C++20 real-time rendering loop with lock-free Single-Producer Single-Consumer (SPSC) ring buffers.
- **Modular Plugin Engine**: Extensible C-ABI plugin host supporting polyphonic synths, dynamic insert FX, and runtime APK sideloading.
- **Cubase-Style Arranger**: Dual-axis zoom, multi-clip marquee selection, track reparenting, slip editing, dual-edge trimming, and transactional undo/redo.
- **Studio MIDI Piano Roll**: Scale-fold keybed, scale-snap intelligence, chord stamper presets, velocity automation, and note chop/slice tools.
- **Non-Destructive Wave Editor**: Mipmapped peak caches, spectral flux transient detection, zero-crossing snapping, WSOLA time-stretching, and pitch shifting.
- **Mixing Console**: 32-track bus mixer, 4-band parametric EQ, studio compressor, algorithmic reverb, stereo delay, and master brickwall limiter.

---

## Prerequisites

- **Python 3.10+**
- **Android SDK** (API Level 34 Platform, Build-Tools 34.0.0+)
- **Android NDK** (Clang with C++20 support) or **Termux Native Clang** on Android
- Java Development Kit (**JDK 17**)

Check your environment anytime with:
```bash
python3 tools/doctor.py
```

---

## Build Instructions

### Quick Incremental Build:
```bash
./build.sh
```

### Full Clean & Dependency Resolution Build:
```bash
./buildfull.sh
```

The signed output APK will be generated at `out/apk/Cobass-release.apk`.

---

## License & Policy

Built under the strict **No-Gradle Architecture Policy** (`NO_GRADLE_POLICY.md`). All rights reserved.
EOF

echo "  [✓] README.md created."

echo ""
echo "======================================================================"
echo "==> [3/4] Initializing Git & Creating Initial Commit"
echo "======================================================================"

# Initialize Git repository if not already initialized
if [ ! -d ".git" ]; then
    git init -b main
    echo "  [+] Initialized new Git repository on branch 'main'."
else
    echo "  [*] Existing Git repository detected."
fi

# Stage source code and configuration files
git add .gitignore README.md NO_GRADLE_POLICY.md config/ tools/ app/ addons/ docs/ plan/ *.sh

# Commit
git commit -m "feat: Initial commit of Cobass DAW (No-Gradle Architecture, AAudio C++20 Engine)" || true

echo "  [✓] Initial commit created successfully."

echo ""
echo "======================================================================"
echo "==> [4/4] Remote GitHub Repository Setup"
echo "======================================================================"

# Option A: GitHub CLI is installed and authenticated
if command -v gh &>/dev/null && gh auth status &>/dev/null; then
    echo "[*] GitHub CLI (gh) detected and authenticated."
    read -rp "Create and push to new GitHub repository now? (y/n): " confirm_gh
    if [[ "$confirm_gh" =~ ^[Yy]$ ]]; then
        read -rp "Enter repository visibility (public/private) [default: private]: " visibility
        visibility="${visibility:-private}"
        gh repo create cobass --"${visibility}" --source=. --remote=origin --push
        echo "======================================================================"
        echo "==> [SUCCESS] Repository created and pushed to GitHub!"
        echo "======================================================================"
        exit 0
    fi
fi

# Option B: Manual Remote Configuration
echo ""
echo "To link and push this repo to GitHub manually:"
echo "  1. Create a new empty repository on https://github.com/new (Name: cobass)"
echo "  2. Run the following commands:"
echo ""
echo "     git remote add origin git@github.com:<YOUR_GITHUB_USERNAME>/cobass.git"
echo "     git branch -M main"
echo "     git push -u origin main"
echo ""
echo "======================================================================"
echo "==> [SUCCESS] Local Git Repository is Ready!"
echo "======================================================================"
EOF


