package se.joynes.nudgealarm.storage

import org.junit.Assert.*
import org.junit.Test

class AlarmSoundTest {

    @Test
    fun allEntriesExist() {
        val sounds = AlarmSound.entries
        assertEquals(6, sounds.size)  // DEFAULT_ALARM, ALARM_CLOCK, DEFAULT_NOTIFICATION, DEFAULT_RINGTONE, BEEP, SILENT
    }

    @Test
    fun defaultAlarmHasCorrectDisplayName() {
        assertEquals("Default Alarm", AlarmSound.DEFAULT_ALARM.displayName)
    }

    @Test
    fun defaultRingtoneHasCorrectDisplayName() {
        assertEquals("Default Ringtone", AlarmSound.DEFAULT_RINGTONE.displayName)
    }

    @Test
    fun defaultNotificationHasCorrectDisplayName() {
        assertEquals("Default Notification", AlarmSound.DEFAULT_NOTIFICATION.displayName)
    }

    @Test
    fun silentHasCorrectDisplayName() {
        assertEquals("Silent", AlarmSound.SILENT.displayName)
    }

    @Test
    fun silentHasNullUri() {
        assertNull(AlarmSound.SILENT.uri)
    }

    // Note: URI tests require Android context (RingtoneManager)
    // These would need to be instrumented tests
    @Test
    fun defaultAlarmIsNotSilent() {
        assertNotEquals(AlarmSound.SILENT, AlarmSound.DEFAULT_ALARM)
    }

    @Test
    fun defaultRingtoneIsNotSilent() {
        assertNotEquals(AlarmSound.SILENT, AlarmSound.DEFAULT_RINGTONE)
    }

    @Test
    fun defaultNotificationIsNotSilent() {
        assertNotEquals(AlarmSound.SILENT, AlarmSound.DEFAULT_NOTIFICATION)
    }

    @Test
    fun fromNameReturnsCorrectSound() {
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmSound.fromName("DEFAULT_ALARM"))
        assertEquals(AlarmSound.DEFAULT_RINGTONE, AlarmSound.fromName("DEFAULT_RINGTONE"))
        assertEquals(AlarmSound.DEFAULT_NOTIFICATION, AlarmSound.fromName("DEFAULT_NOTIFICATION"))
        assertEquals(AlarmSound.SILENT, AlarmSound.fromName("SILENT"))
    }

    @Test
    fun fromNameWithInvalidNameReturnsDefaultAlarm() {
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmSound.fromName("INVALID"))
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmSound.fromName(""))
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmSound.fromName("unknown"))
    }

    @Test
    fun fromNameIsCaseSensitive() {
        // lowercase should not match
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmSound.fromName("default_alarm"))
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmSound.fromName("Silent"))
    }

    @Test
    fun ordinalOrder() {
        assertEquals(0, AlarmSound.DEFAULT_ALARM.ordinal)
        assertEquals(1, AlarmSound.ALARM_CLOCK.ordinal)
        assertEquals(2, AlarmSound.DEFAULT_NOTIFICATION.ordinal)
        assertEquals(3, AlarmSound.DEFAULT_RINGTONE.ordinal)
        assertEquals(4, AlarmSound.BEEP.ordinal)
        assertEquals(5, AlarmSound.SILENT.ordinal)
    }

    @Test
    fun nameProperty() {
        assertEquals("DEFAULT_ALARM", AlarmSound.DEFAULT_ALARM.name)
        assertEquals("DEFAULT_RINGTONE", AlarmSound.DEFAULT_RINGTONE.name)
        assertEquals("DEFAULT_NOTIFICATION", AlarmSound.DEFAULT_NOTIFICATION.name)
        assertEquals("SILENT", AlarmSound.SILENT.name)
    }
}
