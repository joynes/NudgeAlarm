package se.joynes.nudgealarm.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForegroundServiceConfigurationTest {

    @Test
    fun reminderServiceUsesSpecialUseInsteadOfDataSync() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, ReminderService::class.java),
            PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )

        assertTrue(
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
        )
        assertEquals(
            0,
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        val subtype = context.packageManager.getProperty(
            "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
            ComponentName(context, ReminderService::class.java)
        )
        assertEquals(
            "Keeps user-configured reminder schedules and active reminder notifications running while the app is not open.",
            subtype.string
        )
    }
}
