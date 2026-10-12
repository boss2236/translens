package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_snapshots")
data class SavedSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val targetLanguage: String = "en",
    val detectedCount: Int = 0,
    val summarySnippet: String = "",
    val imagePath: String = ""
)

@Entity(tableName = "browsing_history")
data class BrowsingHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "translation_cache", primaryKeys = ["sourceText", "targetLang"])
data class TranslationCacheEntry(
    val sourceText: String,
    val targetLang: String,
    val translatedText: String,
    val detectedLang: String,
    val cachedAt: Long = System.currentTimeMillis()
)
