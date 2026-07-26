# NudgeAlarm

NudgeAlarm is an Android reminder app built around persistent, game-inspired task nudges. Reminders are stored locally on the device and can be managed as quests/routines.

## Features

- Persistent reminder service with notifications.
- Quest-style reminder management.
- Local storage for reminders, settings, history, and app state.
- Import/export support for reminder data.
- Analytics and event log screens for reviewing reminder activity.

## Privacy

NudgeAlarm does not collect, transmit, or share personal data with third parties. App data is stored locally on the device.

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Requirements

- Android Studio
- Android Studio bundled JDK
- Gradle wrapper included in this repository

Use the bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Build And Test

Run unit tests:

```bash
./gradlew test
```

Build a signed release APK:

```bash
./gradlew assembleRelease
```

Release build and upload details are documented in [upload.md](upload.md).

## Signing

Release signing uses local files that are intentionally not committed:

- `keystore.properties`
- release keystore files

Do not commit signing keys, local Android SDK paths, generated APKs, or Play Console credentials.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
