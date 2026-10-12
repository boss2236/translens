package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransLensDao {

    // Saved Snapshots
    @Query("SELECT * FROM saved_snapshots ORDER BY timestamp DESC")
    fun getAllSnapshots(): Flow<List<SavedSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SavedSnapshot): Long

    @Delete
    suspend fun deleteSnapshot(snapshot: SavedSnapshot)

    // History
    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<BrowsingHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: BrowsingHistoryItem): Long

    @Query("DELETE FROM browsing_history")
    suspend fun clearHistory()

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getBookmarks(): Flow<List<BookmarkItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkItem): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkItem)

    // Translation Cache
    @Query("SELECT * FROM translation_cache WHERE sourceText = :text AND targetLang = :targetLang LIMIT 1")
    suspend fun getCachedTranslation(text: String, targetLang: String): TranslationCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedTranslation(entry: TranslationCacheEntry)
}
