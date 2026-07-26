package se.joynes.nudgealarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.joynes.nudgealarm.database.AnalyticsRepository
import se.joynes.nudgealarm.database.DailyAggregate
import se.joynes.nudgealarm.database.NagDatabase
import se.joynes.nudgealarm.database.RuleStats

enum class TimeRange {
    WEEK,
    MONTH,
    YEAR,
    ALL
}

data class ChartDataPoint(
    val date: Long,
    val label: String,
    val completed: Int,
    val expired: Int,
    val cancelled: Int
) {
    val total: Int get() = completed + expired + cancelled
}

data class AnalyticsUiState(
    val timeRange: TimeRange = TimeRange.WEEK,
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val completedCount: Int = 0,
    val expiredCount: Int = 0,
    val cancelledCount: Int = 0,
    val completionRate: Float = 0f,
    val chartData: List<ChartDataPoint> = emptyList(),
    val ruleStats: List<RuleStats> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalBattles: Int = 0,
    val avgResponseTimeMs: Long = 0L
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val analyticsRepository: AnalyticsRepository

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        val database = NagDatabase.getInstance(application)
        analyticsRepository = AnalyticsRepository(database.nagHistoryDao())
        loadData()
    }

    fun selectTimeRange(range: TimeRange) {
        _uiState.value = _uiState.value.copy(timeRange = range, isLoading = true)
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentRange = _uiState.value.timeRange
                val (startTime, endTime) = when (currentRange) {
                    TimeRange.WEEK -> analyticsRepository.getLastWeekRange()
                    TimeRange.MONTH -> analyticsRepository.getLastMonthRange()
                    TimeRange.YEAR -> analyticsRepository.getLastYearRange()
                    TimeRange.ALL -> analyticsRepository.getAllTimeRange()
                }

                val hasData = analyticsRepository.hasData()
                val outcomeCounts = analyticsRepository.getOutcomeCounts(startTime, endTime)
                val dailyAggregates = analyticsRepository.getDailyStats(startTime, endTime)
                val ruleStats = analyticsRepository.getRuleStats(startTime, endTime)

                val total = outcomeCounts.completed + outcomeCounts.expired + outcomeCounts.cancelled
                val completionRate = if (total > 0) {
                    outcomeCounts.completed.toFloat() / total
                } else {
                    0f
                }

                val chartData = buildChartData(dailyAggregates, startTime, endTime, currentRange)

                // Compute streaks: days where completion rate > 50%
                val streaks = computeStreaks(chartData)

                // Average response time across all rules
                val avgResponse = if (ruleStats.isNotEmpty()) {
                    ruleStats.filter { it.avgResponseTimeMs > 0 }
                        .map { it.avgResponseTimeMs }
                        .average().toLong()
                } else 0L

                _uiState.value = AnalyticsUiState(
                    timeRange = currentRange,
                    isLoading = false,
                    hasData = hasData,
                    completedCount = outcomeCounts.completed,
                    expiredCount = outcomeCounts.expired,
                    cancelledCount = outcomeCounts.cancelled,
                    completionRate = completionRate,
                    chartData = chartData,
                    ruleStats = ruleStats,
                    currentStreak = streaks.first,
                    bestStreak = streaks.second,
                    totalBattles = total,
                    avgResponseTimeMs = avgResponse
                )
            }
        }
    }

    private fun buildChartData(
        aggregates: List<DailyAggregate>,
        startTime: Long,
        endTime: Long,
        timeRange: TimeRange
    ): List<ChartDataPoint> {
        // SQL query uses UTC dates (timestamp / 86400000 * 86400000)
        // We need to match using the same UTC calculation
        val msPerDay = 86400000L
        val aggregateMap = aggregates.associateBy { it.date }

        val dateFormat = when (timeRange) {
            TimeRange.WEEK -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
            TimeRange.MONTH -> java.text.SimpleDateFormat("d", java.util.Locale.getDefault())
            TimeRange.YEAR -> java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
            TimeRange.ALL -> java.text.SimpleDateFormat("MMM yy", java.util.Locale.getDefault())
        }

        val result = mutableListOf<ChartDataPoint>()

        // Use UTC day calculation to match SQL query
        var currentDayUtc = (startTime / msPerDay) * msPerDay
        val endDayUtc = (endTime / msPerDay) * msPerDay

        while (currentDayUtc <= endDayUtc) {
            val agg = aggregateMap[currentDayUtc]

            result.add(ChartDataPoint(
                date = currentDayUtc,
                label = dateFormat.format(java.util.Date(currentDayUtc)),
                completed = agg?.completed ?: 0,
                expired = agg?.expired ?: 0,
                cancelled = agg?.cancelled ?: 0
            ))

            currentDayUtc += msPerDay
        }

        return result
    }

    /**
     * Compute current and best streak of days with >50% completion.
     * Returns Pair(currentStreak, bestStreak).
     */
    private fun computeStreaks(chartData: List<ChartDataPoint>): Pair<Int, Int> {
        var best = 0
        var current = 0
        for (point in chartData) {
            if (point.total > 0 && point.completed.toFloat() / point.total > 0.5f) {
                current++
                if (current > best) best = current
            } else if (point.total > 0) {
                current = 0
            }
            // Skip days with no data (don't break streak)
        }
        return current to best
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadData()
    }
}
