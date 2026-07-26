package se.joynes.nudgealarm.database

enum class NagStatus {
    ACTIVE,      // Nagging in progress
    SNOOZED,     // User snoozed, will resume
    COMPLETED,   // User tapped DONE
    EXPIRED,     // Max nags reached without action
    CANCELLED    // User cancelled from app
}
