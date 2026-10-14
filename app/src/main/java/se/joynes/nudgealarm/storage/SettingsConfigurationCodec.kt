package se.joynes.nudgealarm.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

internal data class SettingsConfiguration(
    val alarmSound: String = "DEFAULT_ALARM",
    val vibrationEnabled: Boolean = true,
    val customSoundUri: String? = null,
    val customSoundName: String? = null,
    val oldReminderRetentionMinutes: Int = 1440,
    val alertOnlyWhenActive: Boolean = false,
    val minAlertIntervalMinutes: Int = 0,
    val snooze: SnoozeConfiguration = SnoozeConfiguration(),
    val clockShortcuts: List<SnoozeClockShortcut> = emptyList(),
    val discussions: Map<String, String> = emptyMap()
)

/** Explicit field names avoid reflection/obfuscation changing the editable file format. */
internal object SettingsConfigurationCodec {
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    private val keys = setOf("alarmSound", "vibrationEnabled", "customSoundUri", "customSoundName",
        "oldReminderRetentionMinutes", "alertOnlyWhenActive", "minAlertIntervalMinutes",
        "durationPresetsMinutes", "quickPickCount", "adaptiveQuickPicks", "recentCount",
        "defaultDurationMinutes", "rememberLastDuration", "defaultSnoozeMode", "clockShortcuts")

    fun encode(config: SettingsConfiguration): String {
        val settings = JsonObject()
        fun field(key: String, value: JsonElement, default: JsonElement, description: String, constraints: String, example: String) {
            settings.add(key, JsonObject().apply {
                add("value", value); add("default", default)
                addProperty("description", description); addProperty("constraints", constraints)
                addProperty("example", example)
                addProperty("discussion", config.discussions[key].orEmpty())
            })
        }
        fun number(value: Int) = JsonPrimitive(value)
        fun boolean(value: Boolean) = JsonPrimitive(value)
        fun nullable(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE
        field("alarmSound", JsonPrimitive(config.alarmSound), JsonPrimitive("DEFAULT_ALARM"),
            "Built-in notification sound used when customSoundUri is null. Import recreates the Android notification channel. Android system channel overrides may still affect sound.",
            "DEFAULT_ALARM, ALARM_CLOCK, DEFAULT_NOTIFICATION, DEFAULT_RINGTONE, BEEP or SILENT", "SILENT disables sound; vibration is configured separately.")
        field("vibrationEnabled", boolean(config.vibrationEnabled), boolean(true),
            "Whether reminder alerts vibrate. Quiet mode and Android system restrictions can suppress vibration regardless of this value.", "Boolean true or false", "false for sound-only alerts.")
        field("customSoundUri", nullable(config.customSoundUri), JsonNull.INSTANCE,
            "Optional device-local sound URI, overriding alarmSound. May not exist or be accessible on another phone. Import never grants storage permissions or downloads a sound.",
            "null, or a content:// or android.resource:// URI up to 2048 characters; customSoundName must also be set when a URI is set", "Use null for portable settings; select a device ringtone in the app afterward.")
        field("customSoundName", nullable(config.customSoundName), JsonNull.INSTANCE,
            "Display label of the custom ringtone. This does not locate a sound or change its permissions.", "null when customSoundUri is null, otherwise a non-blank string of at most 120 characters", "My ringtone")
        field("oldReminderRetentionMinutes", number(config.oldReminderRetentionMinutes), number(1440),
            "Minutes a non-sticky active quest remains before automatic expiry. Sticky quests remain until completed or abandoned. A shorter value can expire old non-sticky quests on the next scheduler evaluation.", "Integer 1..10080 minutes", "1440 = one day; 10080 = seven days.")
        field("alertOnlyWhenActive", boolean(config.alertOnlyWhenActive), boolean(false),
            "Suppress sound and vibration when the screen is off. This does not complete quests or snooze them; other notification gates still apply.", "Boolean true or false", "true to avoid audible alerts while the screen is off.")
        field("minAlertIntervalMinutes", number(config.minAlertIntervalMinutes), number(0),
            "Global minimum interval between audible/vibrating alerts, regardless of an individual quest's nag interval. Does not change quest schedules or snooze duration.", "Integer 0..10080 minutes; 0 means no additional cooldown", "60 = alert at most once per hour.")
        val snooze = config.snooze
        field("durationPresetsMinutes", gson.toJsonTree(snooze.durationPresets.sorted()), gson.toJsonTree(DEFAULT_SNOOZE_PRESETS),
            "Duration shortcuts shared by quest snooze and pause-all. Sorted shortest first. In fixed mode these are the only Quick Picks; adaptive mode may replace choices with learned durations. Removing a preset does not delete recent history.",
            "Array of 0..48 unique integers, each 1..10139 minutes", "[10,30,60,120] for four manually chosen durations.")
        field("quickPickCount", number(snooze.quickPickCount), number(9),
            "Maximum number of duration Quick Picks shown in a three-column grid. Fewer appear when fewer choices exist. Clock shortcuts are not limited by this value.", "Integer 1..24", "9 gives three rows when at least nine choices exist.")
        field("adaptiveQuickPicks", boolean(snooze.adaptive), boolean(true),
            "When true, frequently/recently used duration choices are prioritized, then configured presets fill remaining spaces; displayed choices remain ascending. When false, only configured presets appear. Clock times never enter duration history.", "Boolean true or false", "false for a predictable fixed set of shortcuts.")
        field("recentCount", number(snooze.recentCount), number(6),
            "How many duration history entries appear in RECENT, most recently used first. Zero hides RECENT. History stays on the phone and is not exported.", "Integer 0..20", "0 to hide recent choices; 6 for the usual menu.")
        field("defaultDurationMinutes", number(snooze.defaultMinutes), number(15),
            "Initial duration wheel value when remembering the last duration is off, or there is no local history. This does not automatically pause anything.", "Integer 1..10139 minutes", "30 starts the duration wheel at half an hour.")
        field("rememberLastDuration", boolean(snooze.rememberLast), boolean(true),
            "Restore the most recently used duration on the wheels instead of the configured default. Does not affect named clock shortcuts or remember an active pause across imports.", "Boolean true or false", "false to always start with defaultDurationMinutes.")
        field("defaultSnoozeMode", JsonPrimitive(snooze.defaultMode), JsonPrimitive("duration"),
            "Which tab opens initially for both quest snooze and pause-all. The other tab remains available and choosing a tab alone never snoozes anything.", "duration or clock", "clock to show Lunch/After work shortcuts first.")
        val clocks = com.google.gson.JsonArray()
        config.clockShortcuts.forEach { shortcut -> clocks.add(JsonObject().apply {
            addProperty("id", shortcut.id); addProperty("name", shortcut.name)
            addProperty("hour", shortcut.hour); addProperty("minute", shortcut.minute)
        }) }
        field("clockShortcuts", clocks, com.google.gson.JsonArray(),
            "Named daily local-clock shortcuts shared by quest snooze and pause-all. Applying a shortcut pauses until its next occurrence; a passed or equal time means tomorrow. Time zone and daylight-saving rules use the phone's local zone. Saving a shortcut never creates an automatic daily pause. Scheduling rounds remaining time up to a whole minute so it never resumes early.",
            "Array of 0..48 objects: id = unique non-blank string <=100 characters (stable edit identity); name = non-blank label <=60 characters; hour = integer 0..23; minute = integer 0..59. Empty array removes all named shortcuts.",
            "[{\"id\":\"lunch\",\"name\":\"Lunch\",\"hour\":12,\"minute\":0}]")
        return gson.toJson(JsonObject().apply {
            addProperty("format", "nudgealarm-settings"); addProperty("version", 1)
            addProperty("instructions", "Edit each settings entry's value, keeping its key and JSON type. discussion is free text (maximum 4000 characters per setting) for conversations and is preserved on import/export. Descriptions/defaults/constraints are documentation, never executed. All values are validated before import; review the preview before applying. This file replaces configurable settings only, not quests or places.")
            addProperty("notIncluded", "Quests, saved coordinates, diagnostic logs, permissions, running services, active quiet-mode timers, learned duration history and Android notification channel IDs. Use the separate full-data backup for quest/place data.")
            add("settings", settings)
        })
    }

    fun decode(text: String): SettingsConfiguration {
        require(text.length <= 256_000) { "Settings file exceeds 256 KB" }
        val root = JsonParser.parseString(text).asJsonObject
        require(root["format"]?.asString == "nudgealarm-settings" && root["version"]?.toString() == "1") { "Unsupported settings format/version" }
        val entries = root["settings"]?.asJsonObject ?: error("Missing settings object")
        require(entries.keySet() == keys) { "Settings keys must match the export: missing ${keys - entries.keySet()}, unknown ${entries.keySet() - keys}" }
        fun value(key: String): JsonElement = entries[key].asJsonObject["value"] ?: error("Missing value for $key")
        fun integer(element: JsonElement, key: String): Int {
            require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber && element.toString().matches(Regex("-?[0-9]+"))) { "$key must be an integer" }
            return element.asString.toIntOrNull() ?: error("$key is too large")
        }
        fun int(key: String, range: IntRange): Int = integer(value(key), key).also { require(it in range) { "$key must be in $range" } }
        fun bool(key: String): Boolean = value(key).let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "$key must be boolean" }; it.asBoolean
        }
        fun string(element: JsonElement, key: String): String {
            require(element.isJsonPrimitive && element.asJsonPrimitive.isString) { "$key must be a string" }; return element.asString
        }
        fun nullable(key: String): String? = value(key).let { if (it.isJsonNull) null else string(it, key) }
        val sound = string(value("alarmSound"), "alarmSound")
        require(sound in setOf("DEFAULT_ALARM", "ALARM_CLOCK", "DEFAULT_NOTIFICATION", "DEFAULT_RINGTONE", "BEEP", "SILENT")) { "Unknown alarmSound" }
        val uri = nullable("customSoundUri")
        val name = nullable("customSoundName")
        require(uri == null || (uri.length <= 2048 && (uri.startsWith("content://") || uri.startsWith("android.resource://")))) { "Unsupported customSoundUri" }
        require(if (uri == null) name == null else name != null && name.isNotBlank() && name.length <= 120) { "customSoundName must match customSoundUri" }
        val durations = value("durationPresetsMinutes").asJsonArray.map { integer(it, "durationPresetsMinutes") }
        val clocks = value("clockShortcuts").asJsonArray.map { element ->
            val item = element.asJsonObject
            require(item.keySet() == setOf("id", "name", "hour", "minute")) { "Each clock shortcut needs exactly id, name, hour and minute" }
            SnoozeClockShortcut(string(item["id"], "id"), string(item["name"], "name"), integer(item["hour"], "hour"), integer(item["minute"], "minute"))
        }
        require(clocks.size <= 48 && clocks.distinctBy { it.id }.size == clocks.size && clocks.all { it.id.length <= 100 && it.name.length <= 60 }) { "Invalid or duplicate clock shortcuts" }
        val config = SnoozeConfiguration(durations.sorted(), int("quickPickCount", 1..24), bool("adaptiveQuickPicks"),
            int("recentCount", 0..20), int("defaultDurationMinutes", 1..MAX_SNOOZE_MINUTES), bool("rememberLastDuration"), string(value("defaultSnoozeMode"), "defaultSnoozeMode"))
        val notes = keys.associateWith { key ->
            entries[key].asJsonObject["discussion"]?.let { string(it, "$key.discussion") }.orEmpty().also { require(it.length <= 4000) { "Discussion for $key exceeds 4000 characters" } }
        }
        return SettingsConfiguration(sound, bool("vibrationEnabled"), uri, name, int("oldReminderRetentionMinutes", 1..10080),
            bool("alertOnlyWhenActive"), int("minAlertIntervalMinutes", 0..10080), config, clocks, notes.filterValues { it.isNotEmpty() })
    }

    fun changes(before: SettingsConfiguration, after: SettingsConfiguration): List<String> {
        val old = JsonParser.parseString(encode(before)).asJsonObject["settings"].asJsonObject
        val new = JsonParser.parseString(encode(after)).asJsonObject["settings"].asJsonObject
        return keys.mapNotNull { key ->
            val a = old[key].asJsonObject["value"]; val b = new[key].asJsonObject["value"]
            if (a == b) null else "$key: $a → $b"
        }
    }
}
