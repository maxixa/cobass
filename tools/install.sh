#!/usr/bin/env bash
set -uo pipefail

APK_PATH="${1:-out/apk/Cobass-release.apk}"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK file '$APK_PATH' not found."
    exit 1
fi

echo "============================================================"
echo "Installing Cobass APK: $APK_PATH"
echo "============================================================"

# 1. Try Termux GUI Intent Launcher
if command -v termux-open &>/dev/null; then
    echo "[*] Opening Android Package Installer via termux-open..."
    termux-open "$APK_PATH" && echo "✓ Installer opened. Confirm install on device screen."
    exit 0
fi

# 2. Try Package Manager (pm install)
if command -v pm &>/dev/null; then
    echo "[*] Attempting pm install..."
    INSTALL_OUT=$(pm install -r -d "$APK_PATH" 2>&1)
    if echo "$INSTALL_OUT" | grep -q "Success"; then
        echo -e "\033[92m✓ Successfully installed Cobass!\033[0m"
        exit 0
    else
        echo -e "\033[91m[INSTALL FAILED]\033[0m"
        echo "$INSTALL_OUT"
        exit 1
    fi
fi

# 3. Try ADB
if command -v adb &>/dev/null; then
    echo "[*] Attempting adb install..."
    adb install -r -d "$APK_PATH"
    exit 0
fi

echo "APK ready at: $(realpath "$APK_PATH")"
