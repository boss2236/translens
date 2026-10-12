package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedSnapshot::class,
        BrowsingHistoryItem::class,
        BookmarkItem::class,
        TranslationCacheEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TransLensDatabase : RoomDatabase() {
    abstract fun dao(): TransLensDao

    companion object {
        @Volatile
        private var INSTANCE: TransLensDatabase? = null

        fun getDatabase(context: Context): TransLensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransLensDatabase::class.java,
                    "translens_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
