package se.joynes.nudgealarm.storage

import android.content.Context

/**
 * Persistent storage for app state that needs to survive restarts.
 * This includes the currently loaded game so it can be restored.
 */
class AppStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENT_GAME_ID = "current_game_id"
        private const val KEY_CURRENT_GAME_NAME = "current_game_name"
        private const val KEY_SERVICE_WAS_RUNNING = "service_was_running"
    }

    /**
     * Save the current game ID and name.
     */
    fun setCurrentGame(gameId: String?, gameName: String?) {
        prefs.edit()
            .putString(KEY_CURRENT_GAME_ID, gameId)
            .putString(KEY_CURRENT_GAME_NAME, gameName)
            .apply()
    }

    /**
     * Get the saved current game ID.
     */
    fun getCurrentGameId(): String? {
        return prefs.getString(KEY_CURRENT_GAME_ID, null)
    }

    /**
     * Get the saved current game name.
     */
    fun getCurrentGameName(): String? {
        return prefs.getString(KEY_CURRENT_GAME_NAME, null)
    }

    /**
     * Mark whether the service was running (to restore after reboot).
     */
    fun setServiceWasRunning(running: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SERVICE_WAS_RUNNING, running)
            .apply()
    }

    /**
     * Check if the service was running before shutdown.
     */
    fun wasServiceRunning(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_WAS_RUNNING, false)
    }

    /**
     * Clear all saved state.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
