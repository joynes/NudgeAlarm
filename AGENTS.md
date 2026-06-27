# AGENTS.md

## Build and Release Process

After every change, follow this process:

1. Commit your changes.
2. Bump the version number.
3. Run all tests and ensure they pass.
4. Build a release APK only.
5. Upload the generated APK.

## APK Naming Convention

The release build must always be named:

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
