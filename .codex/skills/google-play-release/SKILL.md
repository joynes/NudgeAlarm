---
name: google-play-release
description: Build, upload, and verify NudgeAlarm releases in Google Play Console. Use when publishing or updating this app on Google Play; do not use for ordinary APK sharing to Google Drive.
---

# Google Play Release

Publish the Android app from this repository without disturbing the separately
installed debug/test package.

## Prepare the release

1. Read the repository `AGENTS.md` and `upload.md`; they remain authoritative.
2. Inspect `git status` and preserve unrelated or untracked user files.
3. Confirm `versionCode` and `versionName` in `app/build.gradle.kts`. The Play
   version code must be higher than the latest version already in Play Console.
4. Use Android Studio's bundled JDK:
   `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
5. Run `./gradlew test` and `./gradlew connectedDebugAndroidTest` when an ADB
   device or emulator is available. The debug application ID is
   `se.joynes.nudgealarm.debug`, so it does not replace the release app.
6. Build both required release artifacts with
   `./gradlew assembleRelease bundleRelease`. The Play artifact is
   `app/build/outputs/bundle/release/app-release.aab`; the signed APK is
   `app/build/outputs/apk/release/app-release.apk`.
7. Run `./upload-apk.sh` to keep `gdrive:apks/latest.apk` current, then verify it
   with `rclone lsl gdrive:apks/latest.apk`.

Never print or upload `keystore.properties`, the keystore, passwords, service
account credentials, or other signing secrets.

## Publish in Play Console

Use the existing signed-in Play Console session for application ID
`se.joynes.nudgealarm`.

1. Inspect the current release dashboard, highest version code, active track,
   warnings, and any draft release before making changes. Do not guess a track.
2. Continue the established release track unless the user requests a different
   one. Do not promote from testing to production merely because a build exists.
3. Upload the newly built `app-release.aab` and verify that Play reports the
   intended version code and no blocking artifact error.
4. Draft concise release notes from the commits included since the version that
   Play Console shows as current. Do not claim changes that are not in the build.
5. Review the countries, rollout percentage, managed publishing state, warnings,
   and the exact action Play will take. Resolve safe metadata issues when they
   are within the user's request; stop for policy declarations or choices that
   require information not present in the repository.
6. Immediately before the final rollout, review, or production submission,
   obtain any action-time confirmation required by the active browser policy.
7. After submission, verify the version, track, rollout percentage, and status
   shown by Play Console. Report whether it is live, in review, processing, or
   only saved as a draft.

Do not report a successful publication based only on an upload toast; the track
status is the completion signal.
