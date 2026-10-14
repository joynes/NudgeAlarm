package se.joynes.nudgealarm.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme

class PauseCountdownUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pauseIconShowsRemainingAndEndTimeAndStillResumesOnTap() {
        val now = System.currentTimeMillis()
        var resumed = false
        rule.setContent { NudgeAlarmTheme { Column {
            PauseAlertIndicator(true) { resumed = true }
            PauseStatusBanner(now + 90 * 60_000L, now) { resumed = true }
        } } }
        rule.onNodeWithText("1h 30m left").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-until").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-icon").performClick()
        rule.runOnIdle { assertTrue(resumed) }
    }

    @Test fun countdownUpdatesAndDisappearsWhenAlertsResume() {
        val initial = System.currentTimeMillis()
        val now = mutableLongStateOf(initial)
        val quiet = mutableStateOf(true)
        rule.setContent { NudgeAlarmTheme { Column {
            PauseAlertIndicator(quiet.value, {})
            if (quiet.value) PauseStatusBanner(initial + 60_000L, now.longValue, {})
        } } }
        rule.onNodeWithText("1m left").assertIsDisplayed()
        rule.runOnIdle { now.longValue = initial + 1000L }
        rule.onNodeWithText("59s left").assertIsDisplayed()
        rule.runOnIdle { now.longValue = initial + 60_000L }
        rule.onNodeWithText("Resuming…").assertIsDisplayed()
        rule.runOnIdle { quiet.value = false }
        rule.onNodeWithTag("pause-alerts-remaining").assertDoesNotExist()
        rule.onNodeWithTag("pause-alerts-until").assertDoesNotExist()
    }

    @Test fun indefinitePauseDoesNotInventAnEndTime() {
        rule.setContent { NudgeAlarmTheme { PauseStatusBanner(0, System.currentTimeMillis(), {}) } }
        rule.onNodeWithText("Until you resume").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-until").assertDoesNotExist()
    }

    @Test fun snoozedQuestShowsCountdownEvenWhenItEndsAfterToday() {
        val now = System.currentTimeMillis()
        rule.setContent { NudgeAlarmTheme { SnoozedQuestCountdown(now + (1440 + 30) * 60_000L, now) } }
        rule.onNodeWithText("1d 30m").assertIsDisplayed()
        rule.onNodeWithContentDescription("Snoozed, 1d 30m left", substring = true).assertExists()
    }

    @Test fun compactPauseLayoutSeparatesHeaderAndKeepsQuestStatusWithItsEndTime() {
        val now = ZonedDateTime.of(2026, 10, 14, 13, 50, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
        val until = now + 220 * 60_000L
        rule.setContent { NudgeAlarmTheme {
            Column(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RetroHeader(true, "nudgealarm backup 20260830 12…", true, {}, {})
                PauseStatusBanner(until, now, {})
                OutlinedCard(border = BorderStroke(1.dp, se.joynes.nudgealarm.ui.theme.MegadriveCyan)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("QUEST LOG", color = se.joynes.nudgealarm.ui.theme.MegadriveCyan)
                        ScheduledQuestRow(ScheduledReminderUi("normal", "Benjamin parkour hämta", "16:30", now, schedule = "30 16 * * 3"), now, {}, {}, {}, {})
                        ScheduledQuestRow(ScheduledReminderUi("paused", "dusch barn", "17:30", until, true, "0 18 * * 2,6"), now, {}, {}, {}, {})
                    }
                }
            }
        } }
        val heading = rule.onNodeWithText("NUDGEALARM").fetchSemanticsNode().boundsInRoot
        val banner = rule.onNodeWithTag("pause-status-banner").fetchSemanticsNode().boundsInRoot
        assertTrue("Countdown must be below the header, not beside its title", heading.bottom < banner.top)
        rule.onNodeWithText("dusch barn").assertIsDisplayed()
        rule.onNodeWithText("3h 40m").assertIsDisplayed()
        rule.onNodeWithText("dusch barn (snoozed)").assertDoesNotExist()
        val name = rule.onNodeWithText("dusch barn", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val badge = rule.onNodeWithTag("snooze-remaining-paused", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val outputDirectory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it) } ?: rule.activity.getExternalFilesDir("layout_previews")!!
        val output = File(outputDirectory, "pause-layout.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Quest countdown must be in the trailing time column", name.right <= badge.left)
    }

    @Test fun bannerResumeButtonWorksOnNarrowScreenWithLongDuration() {
        var resumed = false
        val now = System.currentTimeMillis()
        rule.setContent { NudgeAlarmTheme { Column(Modifier.width(280.dp)) {
            PauseStatusBanner(now + 7 * 24 * 60 * 60_000L, now) { resumed = true }
        } } }
        rule.onNodeWithText("7d left").assertIsDisplayed()
        rule.onNodeWithTag("pause-alerts-until").assertIsDisplayed()
        rule.onNodeWithTag("resume-alerts").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(resumed) }
    }
}
