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

The Android SDK tools are installed at `/Users/joka/Library/Android/sdk`. Use the
separate `NudgeAlarmCoverage` AVD (Pixel 6, Android 36.1, Google Play ARM64) so
another project's emulator and installed apps remain untouched:

```bash
/Users/joka/Library/Android/sdk/emulator/emulator \
  -avd NudgeAlarmCoverage -port 5556 -partition-size 4096 \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect &
/Users/joka/Library/Android/sdk/platform-tools/adb -s emulator-5556 wait-for-device
```

Run all instrumented UI tests against that emulator:

```bash
export ANDROID_SERIAL="emulator-5556"
PATH="/Users/joka/Library/Android/sdk/platform-tools:$PATH" \
  python3 scripts/prepare_test_device.py --serial "$ANDROID_SERIAL"
./gradlew connectedDebugAndroidTest
```

The debug build uses the separate package `se.joynes.nudgealarm.debug`, so running
UI tests does not replace the installed release app.

When no macOS desktop session is logged in, add `-no-window` to the emulator
command to run the same UI tests headlessly. Play Console uploads through the
signed-in Chrome session still require an available macOS desktop session.

On a fresh emulator, enable **Settings → Location → Location services → Location
Accuracy → Improve Location Accuracy** before the geofence integration test.
It needs Google Play services' network location provider as well as the main
Location switch. A simulated position can be supplied with `adb emu geo fix 18.0 59.3`.

For combined JVM/emulator coverage and the 95% gate, follow `TESTING.md`.
Keep several GB of host disk space free. Do not clear another project's emulator
when the host or an AVD runs out of storage.

## Build Release APK and Play App Bundle

```bash
./gradlew assembleRelease bundleRelease
```

The signed release artifacts are generated at:

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

## Upload

```bash
./upload-apk.sh
```

The uploaded APK is always named:

```text
latest.apk
```

## Google Play

Follow `.codex/skills/google-play-release/SKILL.md` to upload the AAB to the
existing production and internal testing tracks. Confirm the intended version
code, review release status, and verify both tracks after submission. The APK
upload to Drive does not publish the app on Google Play.
