package se.joynes.nudgealarm.location

import android.content.Context

// Device-specific presence is never backed up. Unknown always means no alert.
internal class GeofenceStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("geofence_state", Context.MODE_PRIVATE)

    fun replace(specs: List<GeofenceSpec>, generation: String) = synchronized(lock) {
        val edit = prefs.edit().clear().putString("generation", generation).putString("status", "Setting up place monitoring…")
        specs.forEachIndexed { index, spec ->
            edit.putString("spec_${spec.placeId}", spec.signature)
                .putString("request_${spec.placeId}", "$generation:$index")
        }
        edit.apply()
    }

    fun requests(): Map<String, String> = prefs.all.entries
        .filter { it.key.startsWith("request_") }.associate { it.key.removePrefix("request_") to it.value.toString() }

    fun ready(generation: String) = synchronized(lock) {
        if (prefs.getString("generation", null) == generation) {
            prefs.edit().putBoolean("ready", true).putString("status", "Battery-saving place monitoring is on").apply()
        }
    }

    fun transition(requestIds: List<String>, inside: Boolean): Boolean = synchronized(lock) {
        val matches = requests().filterValues { it in requestIds }.keys
        if (matches.isEmpty()) return false
        val edit = prefs.edit()
        matches.forEach { edit.putBoolean("inside_$it", inside) }
        edit.apply()
        true
    }

    fun allows(spec: GeofenceSpec): Boolean = prefs.getBoolean("ready", false) &&
        prefs.getString("spec_${spec.placeId}", null) == spec.signature &&
        prefs.getBoolean("inside_${spec.placeId}", false)

    fun invalidate(message: String) = synchronized(lock) {
        if (prefs.getString("status", null) != message || prefs.getBoolean("ready", false) || requests().isNotEmpty())
            prefs.edit().clear().putString("status", message).apply()
    }

    fun status(): String = prefs.getString("status", null) ?: "Waiting for place monitoring to start"
    companion object { private val lock = Any() }
}
