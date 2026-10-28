# Privacy Policy for NudgeAlarm

**Last updated: 2026-10-28**

## Overview

NudgeAlarm is a personal reminder app that helps you stay on top of your tasks using a game-inspired metaphor (quests and battles).

## Data Collection

NudgeAlarm has no developer-operated server and does not upload your reminders or saved places to the developer.

App data (reminders, saved places, coordinates, settings, preferences) is stored locally. Android backup or an explicitly exported backup may also contain this data, depending on your device settings. Temporary geofence presence state is excluded from Android backup and device transfer.

## Permissions Used

- **Notifications**: To display reminder alerts and persistent notifications.
- **Boot completed**: To reschedule reminders after device restart.
- **Foreground service**: To keep the reminder service running reliably.
- **Optional usage access**: If you choose “Other apps too” for the automatic quiet break, NudgeAlarm queries Android-reported unlock and user-interaction event times before an existing nag evaluation. It does not read screen contents, retain app names, or upload usage history. Without this access it can still detect phone unlocks while the reminder service runs. Local last-use and per-reminder delivery timestamps are excluded from Android backup/device transfer and settings export; they can appear in a diagnostic export that you explicitly choose to share. Access can be revoked in Android Settings.
- **Precise and background location**: Only for place-based reminders. Saved coordinates and radius are provided to Google Play services on your device to register geofences. Android/Google Play services detect entry and exit, and NudgeAlarm reuses the resulting local presence state for all linked reminders instead of repeatedly requesting GPS positions. Their location services are governed by your Android/Google location settings and applicable privacy policies. NudgeAlarm does not upload saved places to its own server. Choosing “Use current location” requests a position once to save a place.
- **Alarm (exact)**: To trigger reminders at precise scheduled times.

These permissions are used only for the app features described above, not for advertising or developer tracking.

## No Ads, No Tracking

NudgeAlarm contains no advertisements and no analytics or tracking services.

## Contact

If you have any questions about this privacy policy, you can contact the developer at:

**See the project issue tracker**
