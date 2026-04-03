package com.todoapp.domain.model

enum class TodoType(val displayName: String) {
    TASK("Task"),      // Syncs to Google Tasks API
    EVENT("Event"),    // Syncs to Google Calendar API
    REMINDER("Reminder") // Syncs to Gmail as a draft
}
