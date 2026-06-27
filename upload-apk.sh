#!/bin/bash
set -e

# Upload APK to Google Drive
# Usage: ./upload-apk.sh [path-to-apk]
# If no path is provided, uses the signed release APK.

APK_PATH="${1:-app/build/outputs/apk/release/app-release.apk}"
GDRIVE_REMOTE="gdrive"
GDRIVE_FOLDER="apks"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    echo "Run ./gradlew assembleRelease first, or provide APK path as argument"
    exit 1
fi

# Check rclone is available
if ! command -v rclone &> /dev/null; then
    echo "Error: rclone not installed. Install with: brew install rclone"
    exit 1
fi

# Create folder if it doesn't exist
rclone mkdir "${GDRIVE_REMOTE}:${GDRIVE_FOLDER}" 2>/dev/null || true

echo "Uploading APK to Google Drive..."

# Upload as latest.apk (overwrites previous)
echo "  → ${GDRIVE_FOLDER}/latest.apk"
rclone copyto "$APK_PATH" "${GDRIVE_REMOTE}:${GDRIVE_FOLDER}/latest.apk"

echo ""
echo "Done! Uploaded release APK as ${GDRIVE_FOLDER}/latest.apk."
