# NudgeAlarm2 Technical Architecture

## Overview

NudgeAlarm2 is a YAML-configured reminder application for Android that uses a foreground service with coroutine-based scheduling. It provides persistent reminders with configurable "nag" loops that repeatedly notify until the user acknowledges completion.

## System Architecture

```
+------------------------------------------------------------------+
|                         ANDROID SYSTEM                            |
+------------------------------------------------------------------+
|                                                                   |
|  +---------------------------+    +---------------------------+   |
|  |      MainActivity         |    |    NotificationManager    |   |
|  |  +---------------------+  |    +---------------------------+   |
|  |  |   NudgeAlarmApp     |  |              ^                     |
|  |  |   (Composable)      |  |              |                     |
|  |  +----------+----------+  |              |                     |
|  +-------------|-------------+              |                     |
|                |                            |                     |
|                v                            |                     |
|  +---------------------------+              |                     |
|  |      MainViewModel        |              |                     |
|  |  - uiState (StateFlow)    |              |                     |
|  |  - startPolling()         |              |                     |
|  |  - loadPreset()           |              |                     |
|  +-------------|-------------+              |                     |
|                |                            |                     |
|                | Intent                     |                     |
|                v                            |                     |
|  +------------------------------------------+-------------------+ |
|  |                    ReminderService (Foreground)              | |
|  |                                                              | |
|  |  +------------------+  +------------------+  +--------------+| |
|  |  | schedulerJob     |  | nagJobs (Map)    |  | stateManager || |
|  |  | (Coroutine)      |  | (per reminder)   |  |              || |
|  |  +--------+---------+  +--------+---------+  +--------------+| |
|  |           |                     |                            | |
|  |           v                     v                            | |
|  |  +------------------+  +------------------+                  | |
|  |  | Poll every 5min  |  | Nag loop per     |                  | |
|  |  | Check all rules  |  | active reminder  |                  | |
|  |  +------------------+  +------------------+                  | |
|  +--------------------------------------------------------------+ |
|                |                            |                     |
|                v                            v                     |
|  +---------------------------+  +---------------------------+     |
|  |     CronExpression        |  |   ReminderNotification    |     |
|  |  - parse(schedule)        |  |  - buildNotification()    |     |
|  |  - nextTriggerTime()      |  |  - DONE/SNOOZE actions    |     |
|  +---------------------------+  +---------------------------+     |
|                                                                   |
+-------------------------------------------------------------------+
```

## Package Structure

```
org.nudgealarm.app/
├── core/                    # Pure Kotlin (no Android dependencies)
│   ├── config/
│   │   ├── AppConfig.kt         # Root config data class
│   │   ├── ReminderConfig.kt    # Individual reminder configuration
│   │   └── ConfigParser.kt      # YAML parsing logic
│   ├── cron/
│   │   ├── CronExpression.kt    # Cron expression parser & scheduler
│   │   └── CronField.kt         # Field types (Single, Range, List, Any)
│   ├── state/
│   │   ├── ReminderState.kt     # Per-reminder state tracking (legacy)
│   │   └── StateManager.kt      # Done/snooze state management (legacy)
│   └── event/
│       └── Event.kt             # Sealed class for all event types
│
├── database/                # Room database for persistent nag state
│   ├── NagStatus.kt            # Enum: ACTIVE, SNOOZED, COMPLETED, EXPIRED
│   ├── NagStateEntity.kt       # Room entity for nag occurrences
│   ├── NagStateDao.kt          # DAO with atomic deduplication
│   ├── NagDatabase.kt          # Singleton Room database
│   └── NagRepository.kt        # Business logic for nag operations
│
├── storage/
│   ├── ConfigLoader.kt          # Load YAML from URI
│   └── EventLogStore.kt         # SharedPreferences-based event log
│
├── notification/
│   ├── ChannelSetup.kt          # Notification channel configuration
│   └── ReminderNotification.kt  # Build reminder & service notifications
│
├── service/
│   ├── ReminderService.kt       # Main foreground service
│   └── ActionReceiver.kt        # Handle notification button actions
│
└── ui/
    ├── MainActivity.kt          # Entry point, navigation
    ├── MainViewModel.kt         # UI state management, preset creation
    ├── MainScreen.kt            # Primary UI with schedule display
    ├── PermissionScreen.kt      # Permission management
    ├── EventLogScreen.kt        # Debug event log viewer
    ├── StatusScreen.kt          # Service status details
    └── RoutinesScreen.kt        # View all loaded reminders
```

## Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         DATA FLOW                                │
└─────────────────────────────────────────────────────────────────┘

1. CONFIGURATION FLOW
   ┌──────────┐    ┌───────────┐    ┌──────────────┐    ┌────────┐
   │  Preset  │───>│ YAML File │───>│ ConfigLoader │───>│ Service│
   │  or File │    │ (stored)  │    │ (SnakeYAML)  │    │        │
   └──────────┘    └───────────┘    └──────────────┘    └────────┘

2. SCHEDULING FLOW
   ┌─────────┐    ┌───────────────┐    ┌──────────────┐
   │ Service │───>│ CronExpression│───>│ nextTrigger  │
   │  Loop   │    │    .parse()   │    │   Time()     │
   └─────────┘    └───────────────┘    └──────────────┘
        │                                     │
        v                                     v
   ┌─────────┐    ┌───────────────┐    ┌──────────────┐
   │  Check  │───>│ shouldFire?   │───>│ fireReminder │
   │  Rules  │    │ (time match)  │    │    ()        │
   └─────────┘    └───────────────┘    └──────────────┘

3. NOTIFICATION FLOW
   ┌──────────────┐    ┌───────────────┐    ┌──────────────┐
   │ fireReminder │───>│ Notification  │───>│   Android    │
   │              │    │   Builder     │    │ Notification │
   └──────────────┘    └───────────────┘    └──────────────┘
                              │
                              v
                       ┌──────────────┐
                       │ DONE/SNOOZE  │
                       │   Actions    │
                       └──────────────┘

4. STATE FLOW
   ┌──────────────┐    ┌───────────────┐    ┌──────────────┐
   │ User Action  │───>│ StateManager  │───>│ Cancel Nag   │
   │ (DONE/SNOOZE)│    │ .markDone()   │    │   Loop       │
   └──────────────┘    └───────────────┘    └──────────────┘
```

## Core Components

### 1. ReminderService (Foreground Service)

The heart of the application. Runs as an Android foreground service with `START_STICKY` for automatic restart.

**Key responsibilities:**
- Maintain foreground notification (required for background execution)
- Run scheduler coroutine loop (polls every 5 minutes)
- Manage per-reminder nag loops
- Handle user actions (DONE, SNOOZE)
- Persist config URI across restarts

**Scheduler Loop:**
```
while (isActive) {
    for each rule in config.reminders:
        cron = CronExpression.parse(rule.schedule)
        if cron.shouldFireNow() and not firedThisCycle:
            fireReminder(rule)

    sleep(5 minutes)
}
```

### 2. CronExpression (Cron Parser)

Standard 5-field cron format: `minute hour day month weekday`

**Supported syntax:**
| Pattern | Example | Description |
|---------|---------|-------------|
| `*` | `* * * * *` | Every minute |
| Single | `30 7 * * *` | 7:30 AM daily |
| Range | `0 9 * * 1-5` | 9 AM weekdays |
| List | `0 8,12,18 * * *` | 8 AM, noon, 6 PM |
| Mixed | `0 7 * * 0,6` | 7 AM weekends |

**Day matching logic:**
- `dayOfMonth=*` AND `dayOfWeek=*` → matches every day
- `dayOfMonth=*` AND `dayOfWeek=specific` → only check weekday
- `dayOfMonth=specific` AND `dayOfWeek=*` → only check day of month
- Both specific → match on EITHER (OR logic, per cron standard)

### 3. Nag Loop System

Each triggered reminder spawns its own coroutine that repeatedly notifies:

```
nagLoop(rule):
    nagCount = 1
    while nagCount < maxNags and not isDone(ruleId):
        sleep(nagInterval)

        if isSnoozed(ruleId):
            waitUntil(snoozedUntil)

        nagCount++
        updateNotification(nagCount)
```

### 4. Event Logging

All significant events are logged to SharedPreferences with JSON serialization:

```
Events: ServiceStarted, ConfigLoaded, ReminderTriggered,
        NagFired, UserTappedDone, UserTappedSnooze, etc.
```

Max 500 events with FIFO removal. Uses Gson with `registerTypeHierarchyAdapter` for sealed class serialization.

## UI Architecture

### Jetpack Compose + ViewModel

```
┌────────────────────────────────────────────────────┐
│                 MainActivity                        │
│  ┌──────────────────────────────────────────────┐  │
│  │              NudgeAlarmApp                    │  │
│  │  ┌────────────────────────────────────────┐  │  │
│  │  │           MainViewModel                 │  │  │
│  │  │  - uiState: StateFlow<MainUiState>     │  │  │
│  │  │  - Polls service state every 2s        │  │  │
│  │  └────────────────────────────────────────┘  │  │
│  │                     │                        │  │
│  │                     v                        │  │
│  │  ┌────────────────────────────────────────┐  │  │
│  │  │     Screen (based on navigation)       │  │  │
│  │  │  - MainScreen (schedule + reminders)   │  │  │
│  │  │  - PermissionScreen                    │  │  │
│  │  │  - EventLogScreen                      │  │  │
│  │  │  - RoutinesScreen                      │  │  │
│  │  └────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
```

### MainUiState

```kotlin
data class MainUiState(
    val isServiceRunning: Boolean,
    val configFilePath: String?,
    val nextTriggerRule: String?,
    val nextTriggerTime: String?,
    val activeRulesCount: Int,
    val events: List<Event>,
    val activeReminders: List<ActiveReminderUi>,  // Need action
    val todaysSchedule: List<ScheduledReminderUi> // Upcoming today
)
```

## Configuration Format

### YAML Structure

```yaml
reminders:
  - id: unique_rule_id
    title: "Display Title"
    schedule: "30 7 * * 1-5"    # Cron expression
    nag_interval: 5m            # Time between nags
    max_nags: 100               # Maximum nag count
    sound: alarm                # Notification sound
    vibration: strong           # Vibration pattern
    snooze_options:
      - 5m
      - 15m
      - 1h
```

### Built-in Presets

| Preset | Description | Reminders |
|--------|-------------|-----------|
| Test 1 min | Triggers 1 minute from load | 1 |
| Test Hourly | Every full hour | 1 |
| Test Concurrent | 3 events every 30 min | 3 |
| Sample user | Morning/evening routines | 17 |
| Stephanie Routines | Gym, studies, food | 9 |

## Notification System

### Channels

- **Reminder Channel** (`reminder_channel`): IMPORTANCE_HIGH, bypass DND
- **Service Channel** (`service_channel`): IMPORTANCE_LOW, ongoing

### Notification Actions

```
┌─────────────────────────────────────────┐
│  Reminder Title (Nag #3/100)            │
│  Triggered at 07:30                     │
│                                         │
│  [  DONE  ]  [ SNOOZE 5m ] [ SNOOZE 15m]│
└─────────────────────────────────────────┘
```

## Permissions Required

| Permission | Purpose |
|------------|---------|
| `POST_NOTIFICATIONS` | Display reminders |
| `FOREGROUND_SERVICE` | Run background service |
| `FOREGROUND_SERVICE_DATA_SYNC` | Service type |
| `WAKE_LOCK` | Fire reminders while dozing |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent service kill |

## Threading Model

```
┌─────────────────────────────────────────────────────────┐
│                    COROUTINE SCOPES                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ReminderService.serviceScope (SupervisorJob + Default)  │
│  ├── schedulerJob (single loop)                          │
│  └── nagJobs[ruleId] (one per active reminder)          │
│                                                          │
│  MainViewModel.viewModelScope                            │
│  └── UI polling coroutine (every 2s)                    │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

## State Persistence

| Data | Storage | Lifetime |
|------|---------|----------|
| Config URI | SharedPreferences | Survives restart |
| Event Log | SharedPreferences (JSON) | Until cleared |
| Nag State | Room Database (SQLite) | Survives restart, auto-cleanup after 24h |
| Active Reminders | Synced from database | Database-backed |

## Persistent Nag Architecture

Nag states are persisted in SQLite via Room database for atomic deduplication and service restart recovery.

### Occurrence Key

Each reminder occurrence is uniquely identified by: `ruleId@scheduledTime`

```
Example: "vitaminer@1706684400000"
         │         │
         │         └── Scheduled trigger time (epoch ms, rounded to minute)
         └── Rule ID from config
```

### Deduplication Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    ATOMIC DEDUPLICATION                          │
└─────────────────────────────────────────────────────────────────┘

Scheduler Poll:
    for each rule:
        scheduledTime = cron.matchingTime(now)
        occurrenceKey = "ruleId@scheduledTime"

        INSERT INTO nag_states (...) VALUES (...)
        ON CONFLICT (occurrenceKey) DO NOTHING

        if (rowsInserted > 0):
            fireReminder()  // New occurrence
        else:
            // Duplicate, silently ignore
```

### Database Schema

```sql
CREATE TABLE nag_states (
    occurrence_key TEXT PRIMARY KEY,  -- "ruleId@scheduledTime"
    rule_id TEXT NOT NULL,
    scheduled_time INTEGER NOT NULL,
    title TEXT NOT NULL,
    triggered_at INTEGER NOT NULL,
    nag_count INTEGER NOT NULL,
    max_nags INTEGER NOT NULL,
    nag_interval_ms INTEGER NOT NULL,
    status TEXT NOT NULL,             -- ACTIVE, SNOOZED, COMPLETED, EXPIRED
    snoozed_until INTEGER,
    last_nag_at INTEGER
);
```

### Service Restart Recovery

```
┌─────────────────────────────────────────────────────────────────┐
│                    SERVICE RESTART FLOW                          │
└─────────────────────────────────────────────────────────────────┘

1. Service.onCreate()
   └── Initialize NagRepository from Room database

2. Service.startService()
   └── resumeActiveNags()
       ├── SELECT * FROM nag_states WHERE status IN ('ACTIVE', 'SNOOZED')
       ├── For each active nag:
       │   ├── Restore notification
       │   └── Resume nag loop coroutine
       └── Sync activeReminders map for UI

3. Normal operation continues with persistent state
```

## Error Handling

1. **Config errors**: Logged to event log, service shows "Config error" status
2. **Cron parse errors**: Per-rule catch, skips invalid rules
3. **Service killed**: `START_STICKY` triggers auto-restart, resumes from saved config URI
4. **Notification errors**: Caught and logged, doesn't crash service

## Version History

Current version: **15**

Version displayed in UI header for easy debugging and user support.
