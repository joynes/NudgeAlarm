package org.nudgealarm.app.ui

import org.junit.Assert.*
import org.junit.Test
import org.nudgealarm.app.storage.AlarmSound

class SettingsUiStateTest {

    @Test
    fun defaultUiState() {
        val state = SettingsUiState()

        assertEquals(AlarmSound.DEFAULT_ALARM, state.alarmSound)
        assertTrue(state.vibrationEnabled)
    }

    @Test
    fun uiStateWithCustomSound() {
        val state = SettingsUiState(
            alarmSound = AlarmSound.SILENT,
            vibrationEnabled = true
        )

        assertEquals(AlarmSound.SILENT, state.alarmSound)
    }

    @Test
    fun uiStateWithVibrationDisabled() {
        val state = SettingsUiState(
            alarmSound = AlarmSound.DEFAULT_ALARM,
            vibrationEnabled = false
        )

        assertFalse(state.vibrationEnabled)
    }

    @Test
    fun uiStateAllSounds() {
        // Test with each alarm sound option
        for (sound in AlarmSound.entries) {
            val state = SettingsUiState(alarmSound = sound)
            assertEquals(sound, state.alarmSound)
        }
    }

    @Test
    fun uiStateCopyAlarmSound() {
        val original = SettingsUiState(
            alarmSound = AlarmSound.DEFAULT_ALARM,
            vibrationEnabled = true
        )

        val updated = original.copy(alarmSound = AlarmSound.DEFAULT_NOTIFICATION)

        assertEquals(AlarmSound.DEFAULT_NOTIFICATION, updated.alarmSound)
        assertEquals(original.vibrationEnabled, updated.vibrationEnabled)
    }

    @Test
    fun uiStateCopyVibration() {
        val original = SettingsUiState(
            alarmSound = AlarmSound.DEFAULT_ALARM,
            vibrationEnabled = true
        )

        val updated = original.copy(vibrationEnabled = false)

        assertFalse(updated.vibrationEnabled)
        assertEquals(original.alarmSound, updated.alarmSound)
    }

    @Test
    fun uiStateEquality() {
        val state1 = SettingsUiState(alarmSound = AlarmSound.DEFAULT_ALARM, vibrationEnabled = true)
        val state2 = SettingsUiState(alarmSound = AlarmSound.DEFAULT_ALARM, vibrationEnabled = true)

        assertEquals(state1, state2)
        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun uiStateInequality() {
        val state1 = SettingsUiState(alarmSound = AlarmSound.DEFAULT_ALARM, vibrationEnabled = true)
        val state2 = SettingsUiState(alarmSound = AlarmSound.SILENT, vibrationEnabled = true)
        val state3 = SettingsUiState(alarmSound = AlarmSound.DEFAULT_ALARM, vibrationEnabled = false)

        assertNotEquals(state1, state2)
        assertNotEquals(state1, state3)
    }

    @Test
    fun uiStateSilentWithNoVibration() {
        // Test "do not disturb" mode
        val state = SettingsUiState(
            alarmSound = AlarmSound.SILENT,
            vibrationEnabled = false
        )

        assertEquals(AlarmSound.SILENT, state.alarmSound)
        assertFalse(state.vibrationEnabled)
    }
}
