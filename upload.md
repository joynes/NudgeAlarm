# Release Upload Instructions

## Java

Use the JDK bundled with Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Run All Tests

```bash
./gradlew test
```

## Run UI Tests on an Emulator

The Android SDK tools are installed at `/Users/joka/Library/Android/sdk`. Start the
Android 16 test emulator when no device is already running:

```bash
/Users/joka/Library/Android/sdk/emulator/emulator \
  -avd Medium_Phone_API_36.1 \
  -no-audio -no-boot-anim -gpu swiftshader_indirect &
/Users/joka/Library/Android/sdk/platform-tools/adb wait-for-device
```

Run all instrumented UI tests against that emulator:

```bash
export ANDROID_SERIAL="emulator-5554"
./gradlew connectedDebugAndroidTest
```

The debug build uses the separate package `se.joynes.nudgealarm.debug`, so running
UI tests does not replace the installed release app.

## Build Release APK

```bash
./gradlew assembleRelease
```

The signed release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Upload

```bash
./upload-apk.sh
```

The uploaded APK is always named:

```text
latest.apk
```
