package com.todoapp.di

import android.content.Context
import androidx.room.Room
import com.todoapp.data.local.dao.TodoDao
import com.todoapp.data.local.db.TodoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TodoDatabase =
        Room.databaseBuilder(context, TodoDatabase::class.java, TodoDatabase.DATABASE_NAME)
            .addMigrations(TodoDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTodoDao(db: TodoDatabase): TodoDao = db.todoDao()
}
