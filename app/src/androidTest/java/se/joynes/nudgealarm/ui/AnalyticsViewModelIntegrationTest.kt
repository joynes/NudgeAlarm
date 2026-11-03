package se.joynes.nudgealarm.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import se.joynes.nudgealarm.database.*

class AnalyticsViewModelIntegrationTest {
    @Test fun allTimeRangesIncludeEachOutcomeAndEmptyDaysDoNotBreakAStreak() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = NagDatabase.getInstance(app).nagHistoryDao()
        val previous = dao.getInRange(0, Long.MAX_VALUE)
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        try {
            dao.deleteAll()
            val model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(app))[AnalyticsViewModel::class.java]
            withTimeout(10000) { model.uiState.first { !it.isLoading } }
            assertFalse(model.uiState.value.hasData)
            assertEquals(0f, model.uiState.value.completionRate)
            val today = System.currentTimeMillis() - 1000
            val day = 86400000L
            val outcomes = listOf(4 to "COMPLETED", 3 to "EXPIRED", 2 to "COMPLETED", 0 to "COMPLETED", 0 to "CANCELLED")
            outcomes.forEachIndexed { index, (daysAgo, outcome) ->
                val end = today - daysAgo * day
                dao.insert(NagHistoryEntity(ruleId = "analytics-$index", title = "Analytics test", scheduledTime = end - 2000,
                    triggeredAt = end - 1000, completedAt = end, outcome = outcome, nagCount = 1, maxNags = 10, responseTimeMs = 1000))
            }
            for (range in TimeRange.entries) {
                model.selectTimeRange(range)
                val state = withTimeout(10000) { model.uiState.first { !it.isLoading && it.timeRange == range && it.totalBattles == 5 } }
                assertTrue(state.hasData)
                assertEquals(3, state.completedCount)
                assertEquals(1, state.expiredCount)
                assertEquals(1, state.cancelledCount)
                assertEquals(0.6f, state.completionRate)
                assertEquals(1000L, state.avgResponseTimeMs)
                assertEquals(5, state.ruleStats.size)
                assertEquals(5, state.chartData.sumOf { it.total })
                assertTrue(state.chartData.any { it.total == 0 })
                assertEquals(0, state.currentStreak) // Today's 50% completion ends the streak.
                assertEquals(1, state.bestStreak)
            }
            model.refresh()
            withTimeout(10000) { model.uiState.first { !it.isLoading } }
        } finally {
            store.clear()
            dao.deleteAll()
            previous.forEach { dao.insert(it) }
        }
    }
}
