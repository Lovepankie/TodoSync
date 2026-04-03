package com.todoapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.todoapp.data.local.dao.TodoDao
import com.todoapp.data.local.entity.TodoEntity

@Database(
    entities = [TodoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        const val DATABASE_NAME = "todo_sync.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE todo_items ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'"
                )
            }
        }
    }
}
