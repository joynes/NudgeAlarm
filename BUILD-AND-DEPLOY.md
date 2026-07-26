# NudgeAlarm2 Build and Deploy Guide

## Quick Release (TL;DR)

```bash
cd <repo>
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test
./gradlew assembleRelease
./upload-apk.sh
```

The uploaded release APK is always named `latest.apk`.

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

## Test Process

```bash
cd <repo>
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test
```

## Build Process

### Build Release APK
```bash
./gradlew assembleRelease
```

**Output:** `app/build/outputs/apk/release/app-release.apk`

---

## Upload to Google Drive

### Using the upload script
```bash
./upload-apk.sh
```

This uploads one copy:
- `gdrive:apks/latest.apk` - Always the most recent signed release build (overwrites)

### Manual upload
```bash
rclone copyto app/build/outputs/apk/release/app-release.apk gdrive:apks/latest.apk
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
./gradlew clean test assembleRelease
```

---

## Project Details

| Property | Value |
|----------|-------|
| Application ID | `se.joynes.nudgealarm` |
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
./gradlew test && ./gradlew assembleRelease && ./upload-apk.sh
```

Then install from Google Drive on the phone.
