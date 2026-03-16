# NudgeAlarm2 Build and Deploy Guide

## Quick Deploy (TL;DR)

```bash
cd <repo>
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./upload-apk.sh
```

Then open Google Drive on your phone and tap `latest.apk` to install.

---

## Prerequisites

### 1. Java Runtime
The build requires Java. Use the JDK bundled with Android Studio:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### 2. rclone for Google Drive
rclone must be installed and configured:
```bash
# Install (one-time)
brew install rclone

# Configure (one-time)
rclone config
# Choose: n (new remote)
# Name: gdrive
# Type: drive (Google Drive)
# Follow OAuth prompts to authenticate
```

Configuration is stored in `~/.config/rclone/rclone.conf`

---

## Build Process

### Build Debug APK
```bash
cd <repo>
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK (signed)
```bash
./gradlew assembleRelease
```

---

## Upload to Google Drive

### Using the upload script
```bash
./upload-apk.sh
```

This uploads two copies:
- `gdrive:apks/latest.apk` - Always the most recent build (overwrites)
- `gdrive:apks/app-debug-YYYYMMDD-HHMMSS.apk` - Timestamped backup

### Manual upload
```bash
rclone copyto app/build/outputs/apk/debug/app-debug.apk gdrive:apks/latest.apk
```

---

## Installation on Phone

1. Open Google Drive app on your Android phone
2. Navigate to `apks` folder
3. Tap `latest.apk`
4. Allow installation from unknown sources if prompted
5. Install

---

## Troubleshooting

### "Unable to locate a Java Runtime"
Set JAVA_HOME to Android Studio's bundled JDK:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### rclone authentication expired
Re-authenticate:
```bash
rclone config reconnect gdrive:
```

### Build fails with dependency errors
Clean and rebuild:
```bash
./gradlew clean assembleDebug
```

---

## Project Details

| Property | Value |
|----------|-------|
| Application ID | `org.nudgealarm.app` |
| Min SDK | 31 (Android 12) |
| Target SDK | 36 |
| Build Tools | Gradle 9.1.0 |

---

## Remote Deployment via SSH

If deploying from a remote machine (e.g., phone via SSH):

```bash
# Connect to Mac
ssh user@mac-hostname

# Build and upload
cd <repo>
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug && ./upload-apk.sh
```

Then install from Google Drive on the phone.
