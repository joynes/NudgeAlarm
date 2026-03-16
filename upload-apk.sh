#!/bin/bash
set -e

# Upload APK to Google Drive
# Usage: ./upload-apk.sh [path-to-apk]
# If no path provided, uses the default debug APK location

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
GDRIVE_REMOTE="gdrive"
GDRIVE_FOLDER="apks"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    echo "Run ./gradlew assembleDebug first, or provide APK path as argument"
    exit 1
fi

# Check rclone is available
if ! command -v rclone &> /dev/null; then
    echo "Error: rclone not installed. Install with: brew install rclone"
    exit 1
fi

# Create folder if it doesn't exist
rclone mkdir "${GDRIVE_REMOTE}:${GDRIVE_FOLDER}" 2>/dev/null || true

# Generate timestamp for versioned filename
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
VERSIONED_NAME="app-debug-${TIMESTAMP}.apk"

echo "Uploading APK to Google Drive..."

# Upload as latest.apk (overwrites previous)
echo "  → ${GDRIVE_FOLDER}/latest.apk"
rclone copyto "$APK_PATH" "${GDRIVE_REMOTE}:${GDRIVE_FOLDER}/latest.apk"

# Upload versioned copy
echo "  → ${GDRIVE_FOLDER}/${VERSIONED_NAME}"
rclone copyto "$APK_PATH" "${GDRIVE_REMOTE}:${GDRIVE_FOLDER}/${VERSIONED_NAME}"

echo ""
echo "Done! Open Google Drive on your phone and tap latest.apk to install."
