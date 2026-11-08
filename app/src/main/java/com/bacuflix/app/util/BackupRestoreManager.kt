package com.bacuflix.app.util

import com.bacuflix.app.data.FavoritesRepository
import com.bacuflix.app.data.MlRecommendationService
import com.bacuflix.app.data.WatchHistoryRepository
import org.json.JSONArray
import org.json.JSONObject

object BackupRestoreManager {
    const val BACKUP_FORMAT_VERSION = 1

    data class ParsedBackup(
        val version: Int,
        val exportedAt: Long,
        val favorites: JSONArray,
        val watchHistory: JSONArray,
        val mlInteractions: JSONArray
    )

    data class RestoreSummary(
        val favoritesRestored: Int,
        val historyRestored: Int,
        val interactionsRestored: Int
    )

    fun createBackupJsonString(
        favoritesArray: JSONArray,
        historyArray: JSONArray,
        interactionsArray: JSONArray = JSONArray(),
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject().apply {
            put("app", "BACUFlix Native")
            put("version", BACKUP_FORMAT_VERSION)
            put("exportedAt", exportedAt)
            put("favorites", favoritesArray)
            put("watchHistory", historyArray)
            put("mlInteractions", interactionsArray)
        }
        return root.toString(2)
    }

    fun parseBackupJsonString(rawJson: String): ParsedBackup {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Dữ liệu sao lưu trống.")
        }
        val root = JSONObject(trimmed)
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
        val favorites = root.optJSONArray("favorites") ?: JSONArray()
        val history = root.optJSONArray("watchHistory") ?: JSONArray()
        val interactions = root.optJSONArray("mlInteractions") ?: JSONArray()

        if (favorites.length() == 0 && history.length() == 0 && interactions.length() == 0 && !root.has("favorites") && !root.has("watchHistory")) {
            throw IllegalArgumentException("Định dạng JSON không phải bản sao lưu BACUFlix hợp lệ.")
        }

        return ParsedBackup(
            version = version,
            exportedAt = exportedAt,
            favorites = favorites,
            watchHistory = history,
            mlInteractions = interactions
        )
    }

    fun exportAll(
        favoritesRepo: FavoritesRepository,
        watchHistoryRepo: WatchHistoryRepository,
        mlService: MlRecommendationService
    ): String {
        return createBackupJsonString(
            favoritesArray = favoritesRepo.exportToJsonArray(),
            historyArray = watchHistoryRepo.exportToJsonArray(),
            interactionsArray = mlService.exportInteractionsJson()
        )
    }

    fun importAll(
        rawJson: String,
        favoritesRepo: FavoritesRepository,
        watchHistoryRepo: WatchHistoryRepository,
        mlService: MlRecommendationService
    ): RestoreSummary {
        val parsed = parseBackupJsonString(rawJson)
        val favCount = favoritesRepo.importFromJsonArray(parsed.favorites)
        val histCount = watchHistoryRepo.importFromJsonArray(parsed.watchHistory)
        mlService.importInteractionsJson(parsed.mlInteractions)
        return RestoreSummary(
            favoritesRestored = favCount,
            historyRestored = histCount,
            interactionsRestored = parsed.mlInteractions.length()
        )
    }
}
