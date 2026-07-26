package se.joynes.nudgealarm.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Represents a saved game that can be loaded from the game selection list.
 */
data class SavedGame(
    val id: String,           // Unique identifier
    val name: String,         // Display name
    val questCount: Int,      // Number of quests
    val isPreset: Boolean,    // True if this is a built-in preset
    val filePath: String? = null,  // Path to YAML file (for file-based games)
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Persistent storage for saved games list.
 * Allows users to save, load, and delete their game configurations.
 */
class SavedGamesStore(context: Context) {
    private val prefs = context.getSharedPreferences("saved_games", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().create()
    private val listType: Type = object : TypeToken<List<SavedGame>>() {}.type

    /**
     * Get all saved games (custom games only, not presets).
     */
    @Synchronized
    fun getAll(): List<SavedGame> {
        val json = prefs.getString("games", null) ?: return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add or update a saved game.
     */
    @Synchronized
    fun save(game: SavedGame) {
        val games = getAll().toMutableList()
        // Remove existing game with same id
        games.removeAll { it.id == game.id }
        // Add the new/updated game
        games.add(game)
        persist(games)
    }

    /**
     * Delete a saved game by id.
     */
    @Synchronized
    fun delete(id: String) {
        val games = getAll().toMutableList()
        games.removeAll { it.id == id }
        persist(games)
    }

    /**
     * Check if a game with given id exists.
     */
    @Synchronized
    fun exists(id: String): Boolean {
        return getAll().any { it.id == id }
    }

    /**
     * Get a game by id.
     */
    @Synchronized
    fun get(id: String): SavedGame? {
        return getAll().find { it.id == id }
    }

    /**
     * Clear all saved games.
     */
    @Synchronized
    fun clear() {
        prefs.edit().remove("games").apply()
    }

    private fun persist(games: List<SavedGame>) {
        val json = gson.toJson(games, listType)
        prefs.edit().putString("games", json).apply()
    }
}
