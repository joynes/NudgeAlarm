package se.joynes.nudgealarm.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.nudgealarm.notification.QuietModeController

@RunWith(AndroidJUnit4::class)
class SettingsStoreQuietModeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = SettingsStore(context)

    @After
    fun cleanUp() {
        store.quietMode = false
    }

    @Test
    fun timedQuietModePersistsDeadlineAndClearsItWhenDisabled() {
        val deadline = System.currentTimeMillis() + 30 * 60_000L

        store.enableQuietModeUntil(deadline)

        assertTrue(store.quietMode)
        assertEquals(deadline, store.quietModeUntil)

        store.quietMode = false

        assertFalse(store.quietMode)
        assertEquals(0L, store.quietModeUntil)
    }

    @Test
    fun expiredQuietModeIsAutomaticallyDisabled() {
        val now = System.currentTimeMillis()
        store.enableQuietModeUntil(now - 1L)

        val expired = QuietModeController.expireIfNeeded(context, now)

        assertTrue(expired)
        assertFalse(store.quietMode)
        assertEquals(0L, store.quietModeUntil)
    }
}
