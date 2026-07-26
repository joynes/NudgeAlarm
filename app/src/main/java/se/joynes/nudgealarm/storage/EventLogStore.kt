package se.joynes.nudgealarm.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import se.joynes.nudgealarm.core.event.Event
import java.lang.reflect.Type

class EventLogStore(context: Context) {
    private val prefs = context.getSharedPreferences("event_log", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(Event::class.java, EventAdapter())
        .create()
    private val listType: Type = object : TypeToken<List<Event>>() {}.type
    private val maxEvents = 500

    @Synchronized
    fun add(event: Event) {
        val events = getAll().toMutableList()
        events.add(event)
        while (events.size > maxEvents) {
            events.removeAt(0)
        }
        val json = gson.toJson(events, listType)
        prefs.edit().putString("events", json).apply()
    }

    @Synchronized
    fun getAll(): List<Event> {
        val json = prefs.getString("events", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Event>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove("events").apply()
    }

    private class EventAdapter : JsonSerializer<Event>, JsonDeserializer<Event> {
        override fun serialize(
            src: Event,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val jsonObject = JsonObject()
            jsonObject.addProperty("type", src::class.java.simpleName)
            jsonObject.addProperty("timestamp", src.timestamp)

            when (src) {
                is Event.ServiceStarted -> {}
                is Event.ServiceStopped -> jsonObject.addProperty("reason", src.reason)
                is Event.ServiceKilled -> {}
                is Event.ConfigLoaded -> jsonObject.addProperty("ruleCount", src.ruleCount)
                is Event.ConfigReloaded -> jsonObject.addProperty("ruleCount", src.ruleCount)
                is Event.ConfigError -> jsonObject.addProperty("error", src.error)
                is Event.NextEventComputed -> {
                    jsonObject.addProperty("ruleId", src.ruleId)
                    jsonObject.addProperty("triggerTime", src.triggerTime)
                }
                is Event.WaitingUntil -> {
                    jsonObject.addProperty("targetTime", src.targetTime)
                    jsonObject.addProperty("delayMs", src.delayMs)
                }
                is Event.ReminderTriggered -> jsonObject.addProperty("ruleId", src.ruleId)
                is Event.NagFired -> {
                    jsonObject.addProperty("ruleId", src.ruleId)
                    jsonObject.addProperty("nagCount", src.nagCount)
                    jsonObject.addProperty("maxNags", src.maxNags)
                }
                is Event.UserTappedDone -> jsonObject.addProperty("ruleId", src.ruleId)
                is Event.UserTappedSnooze -> {
                    jsonObject.addProperty("ruleId", src.ruleId)
                    jsonObject.addProperty("duration", src.duration)
                }
                is Event.UserTappedCancel -> jsonObject.addProperty("ruleId", src.ruleId)
                is Event.SnoozeEnded -> jsonObject.addProperty("ruleId", src.ruleId)
                is Event.DeviceBooted -> {}
                is Event.BatteryOptimizationChanged -> jsonObject.addProperty("isOptimized", src.isOptimized)
                is Event.AppForegrounded -> {}
                is Event.AppBackgrounded -> {}
                is Event.UiAction -> jsonObject.addProperty("action", src.action)
                is Event.NavigatedTo -> jsonObject.addProperty("screen", src.screen)
                is Event.ConfigFileSelected -> jsonObject.addProperty("path", src.path)
                is Event.TestConfigCreated -> jsonObject.addProperty("configName", src.configName)
                is Event.SchedulerLoopIteration -> jsonObject.addProperty("detail", src.detail)
                is Event.CronParsed -> {
                    jsonObject.addProperty("ruleId", src.ruleId)
                    jsonObject.addProperty("schedule", src.schedule)
                    jsonObject.addProperty("nextTime", src.nextTime)
                }
                is Event.Debug -> jsonObject.addProperty("detail", src.detail)
                is Event.Crash -> {
                    jsonObject.addProperty("exceptionClass", src.exceptionClass)
                    jsonObject.addProperty("exceptionMessage", src.exceptionMessage)
                    jsonObject.addProperty("stackTrace", src.stackTrace)
                }
            }
            return jsonObject
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Event {
            val jsonObject = json.asJsonObject
            val type = jsonObject.get("type").asString
            val timestamp = jsonObject.get("timestamp").asLong

            return when (type) {
                "ServiceStarted" -> Event.ServiceStarted(timestamp)
                "ServiceStopped" -> Event.ServiceStopped(
                    timestamp,
                    jsonObject.get("reason")?.asString ?: "unknown"
                )
                "ServiceKilled" -> Event.ServiceKilled(timestamp)
                "ConfigLoaded" -> Event.ConfigLoaded(
                    timestamp,
                    jsonObject.get("ruleCount").asInt
                )
                "ConfigReloaded" -> Event.ConfigReloaded(
                    timestamp,
                    jsonObject.get("ruleCount").asInt
                )
                "ConfigError" -> Event.ConfigError(
                    timestamp,
                    jsonObject.get("error").asString
                )
                "NextEventComputed" -> Event.NextEventComputed(
                    timestamp,
                    jsonObject.get("ruleId").asString,
                    jsonObject.get("triggerTime").asLong
                )
                "WaitingUntil" -> Event.WaitingUntil(
                    timestamp,
                    jsonObject.get("targetTime").asLong,
                    jsonObject.get("delayMs").asLong
                )
                "ReminderTriggered" -> Event.ReminderTriggered(
                    timestamp,
                    jsonObject.get("ruleId").asString
                )
                "NagFired" -> Event.NagFired(
                    timestamp,
                    jsonObject.get("ruleId").asString,
                    jsonObject.get("nagCount").asInt,
                    jsonObject.get("maxNags")?.asInt ?: 100
                )
                "UserTappedDone" -> Event.UserTappedDone(
                    timestamp,
                    jsonObject.get("ruleId").asString
                )
                "UserTappedSnooze" -> Event.UserTappedSnooze(
                    timestamp,
                    jsonObject.get("ruleId").asString,
                    jsonObject.get("duration").asString
                )
                "UserTappedCancel" -> Event.UserTappedCancel(
                    timestamp,
                    jsonObject.get("ruleId").asString
                )
                "SnoozeEnded" -> Event.SnoozeEnded(
                    timestamp,
                    jsonObject.get("ruleId").asString
                )
                "DeviceBooted" -> Event.DeviceBooted(timestamp)
                "BatteryOptimizationChanged" -> Event.BatteryOptimizationChanged(
                    timestamp,
                    jsonObject.get("isOptimized").asBoolean
                )
                "AppForegrounded" -> Event.AppForegrounded(timestamp)
                "AppBackgrounded" -> Event.AppBackgrounded(timestamp)
                "UiAction" -> Event.UiAction(timestamp, jsonObject.get("action")?.asString ?: "")
                "NavigatedTo" -> Event.NavigatedTo(timestamp, jsonObject.get("screen")?.asString ?: "")
                "ConfigFileSelected" -> Event.ConfigFileSelected(timestamp, jsonObject.get("path")?.asString ?: "")
                "TestConfigCreated" -> Event.TestConfigCreated(timestamp, jsonObject.get("configName")?.asString ?: "")
                "SchedulerLoopIteration" -> Event.SchedulerLoopIteration(timestamp, jsonObject.get("detail")?.asString ?: "")
                "CronParsed" -> Event.CronParsed(
                    timestamp,
                    jsonObject.get("ruleId")?.asString ?: "",
                    jsonObject.get("schedule")?.asString ?: "",
                    jsonObject.get("nextTime")?.asString ?: ""
                )
                "Debug" -> Event.Debug(timestamp, jsonObject.get("detail")?.asString ?: "")
                "Crash" -> Event.Crash(
                    timestamp,
                    jsonObject.get("exceptionClass")?.asString ?: "Unknown",
                    jsonObject.get("exceptionMessage")?.asString ?: "",
                    jsonObject.get("stackTrace")?.asString ?: ""
                )
                else -> Event.Debug(timestamp, "unknown event type: $type")
            }
        }
    }
}
