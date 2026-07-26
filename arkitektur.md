# NudgeAlarm — Arkitektur & Dataflöde

## Lagring — Tre lager

### 1. SharedPreferences (`saved_games`)
Metadata om alla sparade spel. Ingen quest-data, bara:
- `id` — unikt ID
- `name` — visningsnamn
- `questCount` — antal quests
- `isPreset` — om det är en inbyggd preset
- `filePath` — sökväg till YAML-fil (om laddad från fil)
- `createdAt` — tidsstämpel

Hanteras av `SavedGamesStore.kt`.

### 2. YAML-filer i `filesDir`
`/data/data/se.joynes.nudgealarm/files/`

Varje spel sparas som en YAML-fil med alla quest-konfigurationer:
- `game_{id}.yaml` — sparade spel
- `{presetId}.yaml` — presets
- `custom_config.yaml` — export

Innehåller: titel, cron-schema, nag-intervall, max nags per quest.

Hanteras av `ConfigLoader.kt` och `MainViewModel.kt`.

### 3. Room-databas (`nag_database`)
Tre tabeller, version 3, `fallbackToDestructiveMigration`.

#### `reminders` — Aktiva spelets quests
Bara det **nuvarande** spelets quests. Rensas vid spelbyte.
| Kolumn | Typ | Beskrivning |
|---|---|---|
| id | String PK | Unikt quest-ID |
| title | String | Quest-namn |
| schedule | String | Cron-uttryck (t.ex. `30 8 * * 1-5`) |
| nagIntervalMinutes | Int | Minuter mellan påminnelser |
| maxNags | Int | Max antal nags |
| enabled | Boolean | Aktiv/inaktiv |
| createdAt | Long | Skapad |
| updatedAt | Long | Uppdaterad |

#### `nag_states` — Pågående nag-instanser
En rad per triggad quest-instans. Dedupliceras via `occurrenceKey`.
| Kolumn | Typ | Beskrivning |
|---|---|---|
| occurrenceKey | String PK | `ruleId@scheduledTime` |
| ruleId | String | Koppling till reminder |
| scheduledTime | Long | Schemalagd tid |
| title | String | Quest-titel |
| triggeredAt | Long | När den triggades |
| nagCount | Int | Antal nags hittills |
| maxNags | Int | Max nags |
| nagIntervalMs | Long | Intervall i ms |
| status | String | ACTIVE/COMPLETED/EXPIRED/CANCELLED/SNOOZED |
| snoozedUntil | Long? | Snooze-sluttid |
| lastNagAt | Long? | Senaste nag |

#### `nag_history` — Historik (permanent)
Aldrig auto-raderad. Används för High Scores/statistik.
| Kolumn | Typ | Beskrivning |
|---|---|---|
| id | Long auto PK | |
| ruleId | String | Quest-ID |
| title | String | Quest-titel |
| scheduledTime | Long | Schemalagd tid |
| triggeredAt | Long | Triggad tid |
| completedAt | Long | Avslutad tid |
| outcome | String | COMPLETED/EXPIRED/CANCELLED |
| nagCount | Int | Antal nags |
| maxNags | Int | Max nags |
| responseTimeMs | Long | Svarstid |

## Tjänsten — ReminderService

### Uppstart
1. `startService()` → `loadConfigAndStart()`
2. Laddar alla enabled reminders från DB: `dao.getEnabled()` → `AppConfig` (in-memory lista)
3. Återupptar aktiva nags från `nag_states`-tabellen
4. Startar scheduler-loop

### Scheduler-loop (var 5 min)
```
while (isActive) {
    for (rule in appConfig.reminders) {
        checkAndFireRule(rule, now)
    }
    updateNextTriggerDisplay(appConfig)
    syncActiveRemindersFromDatabase()
    delay(5 min)
}
```

### checkAndFireRule — Triggerlogik
1. `CronExpression.parse(rule.schedule)` — parsar cron
2. `cron.nextTriggerTime(now - 2h)` — nästa trigger (tittar 2h bakåt för missade)
3. Om `nextTrigger <= now`:
   - `nagRepository.tryFire(rule, scheduledTime)` — INSERT OR IGNORE i `nag_states`
   - Om ny (inte duplikat) → `fireReminder()` → notifikation + nag-loop

### Nag-loop (per triggad quest)
```
while (active) {
    check status from DB
    if SNOOZED → wait until snoozedUntil, then resume
    if ACTIVE → wait nagInterval, increment nag count, update notification
    if nagCount >= maxNags → mark EXPIRED
    if COMPLETED/EXPIRED/CANCELLED → break
}
```

### Användaråtgärder
- **Done** → `markDoneByRuleId()` → status = COMPLETED, sparar till history
- **Snooze** → `snoozeByRuleId()` → status = SNOOZED, snoozedUntil sätts
- **Cancel** → `markCancelledByRuleId()` → status = CANCELLED, sparar till history

## Spelbyte
1. `reminderRepository.clearAll()` — rensar `reminders`-tabellen
2. `reminderRepository.importFromConfigs(configs)` — fyller med nya quests
3. `appStateStore.setCurrentGame(id, name)` — uppdaterar aktivt spel
4. `savedGamesStore.save(game)` — sparar metadata
5. Servicen laddas om med `restartService()`

## UI-arkitektur
- **MVVM**: ViewModel → Repository → DAO → Room DB
- **MainViewModel** — hanterar spel-load/save, service-styrning, quest log
- **EditRemindersViewModel** — hanterar quest-redigering (CRUD)
- **AnalyticsViewModel** — hanterar statistik/High Scores
- **Jetpack Compose** — all UI
- **Bottom nav**: Quests (main) | Edit | Scores | Options

## Schedule-format
- Cron: `minute hour dayOfMonth month dayOfWeek` (t.ex. `30 8 * * 1-5`)
- One-time: `once:TIMESTAMP` (t.ex. `once:1708444200000`) — triggar en gång, auto-disablas
