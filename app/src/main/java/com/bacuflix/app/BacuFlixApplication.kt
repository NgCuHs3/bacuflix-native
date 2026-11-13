package com.bacuflix.app

import android.app.Application
import com.bacuflix.app.data.CatalogRepository
import com.bacuflix.app.data.FavoritesRepository
import com.bacuflix.app.data.GoogleTrendingService
import com.bacuflix.app.data.KkphimApiService
import com.bacuflix.app.data.MlRecommendationService
import com.bacuflix.app.data.WatchHistoryRepository
import com.bacuflix.app.download.MovieDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BacuFlixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ping original movie server on startup, automatically fallback to proxy if unreachable or blocked
        CoroutineScope(Dispatchers.IO).launch {
            KkphimApiService.pingUpstreamServer()
        }
    }
    val kkphimApi: KkphimApiService by lazy {
        KkphimApiService()
    }

    val movieDownloadManager: MovieDownloadManager by lazy {
        MovieDownloadManager.getInstance(this)
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(this)
    }

    val favoritesRepository: FavoritesRepository by lazy {
        FavoritesRepository(this)
    }

    val watchHistoryRepository: WatchHistoryRepository by lazy {
        WatchHistoryRepository(this)
    }

    val googleTrendingService: GoogleTrendingService by lazy {
        GoogleTrendingService(this, kkphimApi)
    }

    val mlRecommendationService: MlRecommendationService by lazy {
        MlRecommendationService(
            context = this,
            kkphimApi = kkphimApi,
            watchHistoryRepo = watchHistoryRepository,
            favoritesRepo = favoritesRepository
        )
    }
}
