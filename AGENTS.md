# AGENTS.md

## Build and Release Process

After every change, follow this process:

1. Commit your changes.
2. Bump the version number.
3. Run all tests and ensure they pass.
4. Build the signed release APK and Play App Bundle (AAB).
5. Upload the APK as `latest.apk` to Google Drive.
6. Upload and submit the AAB to the existing Google Play production and internal testing tracks. Verify each track's status in Play Console. Follow `.codex/skills/google-play-release/SKILL.md`; do not report publication based on an upload alone.

## APK Naming Convention

The Google Drive release APK must always be named:

```text
latest.apk
```

No version number or timestamp should be included in the file name.

## Build Instructions

The file `upload.md` clearly describes:

- How to build the release APK.
- How to run all tests.
- Where to find the installed Java version.
- Any required environment setup.

Ensure that these instructions are kept up to date.
