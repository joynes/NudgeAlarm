package se.joynes.nudgealarm.database

import androidx.room.withTransaction

/** Change only the place; never overwrite edits or recreate a deleted quest. */
suspend fun NagDatabase.assignReminderPlace(ruleId: String, placeId: String) = withTransaction {
    val place = requireNotNull(savedPlaceDao().getPlace(placeId)) { "This place was deleted. Reopen the picker." }
    val location = place.activeLocationId?.let { savedPlaceDao().getLocation(it) }
    require(location?.placeId == place.id) { "This place needs a saved position. Edit the quest to set it up." }
    check(reminderDao().setPlace(ruleId, placeId) == 1) { "This quest no longer exists." }
}
