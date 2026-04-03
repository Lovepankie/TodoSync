package com.todoapp.domain.model

enum class SyncStatus {
    PENDING,   // Not yet synced to Google
    SYNCED,    // Successfully synced
    FAILED,    // Sync attempt failed
    DELETED    // Marked for deletion on remote
}
