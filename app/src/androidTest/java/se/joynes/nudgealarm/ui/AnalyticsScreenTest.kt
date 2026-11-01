package se.joynes.nudgealarm.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import se.joynes.nudgealarm.ui.theme.NudgeAlarmTheme
import java.io.File

class AnalyticsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allOutcomesAreNamedAndBarsRevealExactCountsIncludingEmptyDays() {
        val days = listOf(ChartDataPoint(0, "", 0, 0, 0), ChartDataPoint(86_400_000, "", 3, 2, 1),
            ChartDataPoint(172_800_000, "", 0, 2, 0), ChartDataPoint(259_200_000, "", 0, 0, 3))
        compose.setContent { NudgeAlarmTheme {
            androidx.compose.material3.Surface {
                AnalyticsScreen(AnalyticsUiState(isLoading = false, hasData = true, completedCount = 3,
                    expiredCount = 4, cancelledCount = 4, completionRate = 3f / 11, chartData = days), {}, {})
            }
        } }
        compose.onNodeWithText("Completed: 3").assertIsDisplayed()
        compose.onNodeWithText("Expired: 4").assertIsDisplayed()
        compose.onNodeWithText("Abandoned: 4").assertIsDisplayed()
        compose.onNodeWithTag("outcome-bar-1").performClick()
        compose.onNodeWithText("3 done • 2 expired • 1 abandoned").assertIsDisplayed()
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { directory ->
            File(directory, "quest-outcomes.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("outcome-bar-0").performClick()
        compose.onNodeWithText("No finished quests in this period").assertIsDisplayed()
        compose.onNodeWithTag("outcome-bar-2").performClick()
        compose.onNodeWithText("0 done • 2 expired • 0 abandoned").assertIsDisplayed()
        compose.onNodeWithTag("outcome-bar-3").performClick()
        compose.onNodeWithText("0 done • 0 expired • 3 abandoned").assertIsDisplayed()
    }
}
