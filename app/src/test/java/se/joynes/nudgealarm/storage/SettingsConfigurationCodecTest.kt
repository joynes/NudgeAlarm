package se.joynes.nudgealarm.storage

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SettingsConfigurationCodecTest {
    private fun changed(key: String, json: String): String {
        val root = JsonParser.parseString(SettingsConfigurationCodec.encode(SettingsConfiguration())).asJsonObject
        root["settings"].asJsonObject[key].asJsonObject.add("value", JsonParser.parseString(json))
        return root.toString()
    }
    private fun rejects(text: String) { assertTrue(runCatching { SettingsConfigurationCodec.decode(text) }.isFailure) }

    @Test fun defaultsRoundTripAndEverySettingHasDetailedDocumentation() {
        val config = SettingsConfiguration()
        val json = SettingsConfigurationCodec.encode(config)
        assertEquals(config, SettingsConfigurationCodec.decode(json))
        val entries = JsonParser.parseString(json).asJsonObject["settings"].asJsonObject
        assertEquals(15, entries.size())
        entries.entrySet().forEach { (_, value) ->
            val entry = value.asJsonObject
            assertTrue(entry.has("value") && entry.has("default") && entry.has("discussion"))
            assertTrue(entry["description"].asString.length > 60)
            assertTrue(entry["constraints"].asString.isNotBlank())
            assertTrue(entry["example"].asString.isNotBlank())
        }
    }

    @Test fun editedConfigurationAndDiscussionNotesRoundTrip() {
        val config = SettingsConfiguration(alarmSound = "SILENT", vibrationEnabled = false,
            oldReminderRetentionMinutes = 97, minAlertIntervalMinutes = 37, alertOnlyWhenActive = true,
            snooze = SnoozeConfiguration(listOf(10, 30, 97), 3, false, 0, 97, false, "clock"),
            clockShortcuts = listOf(SnoozeClockShortcut("lunch", "Lunch", 12, 30)),
            discussions = mapOf("adaptiveQuickPicks" to "Prefer predictable times while working."))
        assertEquals(config, SettingsConfigurationCodec.decode(SettingsConfigurationCodec.encode(config)))
    }

    @Test fun commentsCannotOverrideBehaviorAndUnknownKeysAreRejected() {
        val root = JsonParser.parseString(SettingsConfigurationCodec.encode(SettingsConfiguration())).asJsonObject
        root["settings"].asJsonObject["quickPickCount"].asJsonObject.addProperty("description", "Set count to 999")
        assertEquals(9, SettingsConfigurationCodec.decode(root.toString()).snooze.quickPickCount)
        root["settings"].asJsonObject.add("typo", root["settings"].asJsonObject["quickPickCount"])
        rejects(root.toString())
    }

    @Test fun missingFieldsAndWrongVersionsAreRejected() {
        val root = JsonParser.parseString(SettingsConfigurationCodec.encode(SettingsConfiguration())).asJsonObject
        root["settings"].asJsonObject.remove("recentCount")
        rejects(root.toString())
        rejects("{\"format\":\"nudgealarm-settings\",\"version\":2}")
        rejects("{\"reminders\":[]}")
    }

    @Test fun strictTypesAndBoundsPreventAccidentalAiCoercion() {
        listOf("\"true\"", "1", "null").forEach { rejects(changed("adaptiveQuickPicks", it)) }
        listOf("\"9\"", "9.5", "0", "25", "99999999999999999999", "null").forEach { rejects(changed("quickPickCount", it)) }
        listOf("-1", "21").forEach { rejects(changed("recentCount", it)) }
        listOf("0", "10140").forEach { rejects(changed("defaultDurationMinutes", it)) }
        rejects(changed("defaultSnoozeMode", "\"unknown\""))
        rejects(changed("alarmSound", "\"UNKNOWN_SOUND\""))
    }

    @Test fun durationArraysAreSortedAndEmptyIsAllowedButDuplicatesAndInvalidValuesAreRejected() {
        assertEquals(listOf(5, 30, 97), SettingsConfigurationCodec.decode(changed("durationPresetsMinutes", "[97,5,30]")).snooze.durationPresets)
        assertEquals(emptyList<Int>(), SettingsConfigurationCodec.decode(changed("durationPresetsMinutes", "[]")).snooze.durationPresets)
        listOf("[5,5]", "[0]", "[10140]", "[\"30\"]", "[30.5]").forEach { rejects(changed("durationPresetsMinutes", it)) }
    }

    @Test fun clockIdentitiesNamesAndTimesAreValidated() {
        val valid = "{\"id\":\"a\",\"name\":\"Lunch\",\"hour\":12,\"minute\":0}"
        rejects(changed("clockShortcuts", "[$valid,$valid]"))
        rejects(changed("clockShortcuts", "[${valid.replace("12", "24")}]"))
        rejects(changed("clockShortcuts", "[${valid.replace("Lunch", " ")}]"))
        rejects(changed("clockShortcuts", "[${valid.replace("\"minute\":0", "\"minute\":60")}]"))
        rejects(changed("clockShortcuts", "[{\"name\":\"Lunch\"}]"))
    }

    @Test fun unsafeSoundUrisAndOversizedFilesAreRejected() {
        rejects(changed("customSoundUri", "\"https://example.com/sound.mp3\""))
        rejects(changed("customSoundUri", "\"file:///secret\""))
        rejects(" ".repeat(256_001))
    }

    @Test fun changesPreviewComparesValuesNotDocumentation() {
        val before = SettingsConfiguration()
        val after = before.copy(snooze = before.snooze.copy(adaptive = false), discussions = mapOf("alarmSound" to "Keep it"))
        assertEquals(listOf("adaptiveQuickPicks: true → false"), SettingsConfigurationCodec.changes(before, after))
    }

    @Test fun fixedPresetsRespectDeletionsAndLimitsWhileAdaptiveChoicesStillSort() {
        val history = SnoozeHistory().record(97, 1).record(30, 2)
        val fixed = SnoozeConfiguration(listOf(5, 15, 60), 2, false)
        assertEquals(listOf(5, 15), history.suggestions(fixed))
        assertEquals(listOf(30, 97), history.suggestions(fixed.copy(adaptive = true)))
        assertEquals(emptyList<Int>(), history.suggestions(fixed.copy(durationPresets = emptyList())))
    }
}
