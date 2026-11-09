package com.bacuflix.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.bacuflix.app.ui.home.HomeScreen
import com.bacuflix.app.ui.home.HomeViewModel
import com.bacuflix.app.ui.player.PlayerScreen
import com.bacuflix.app.ui.theme.BACUFlixTheme
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.UrlUtils

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* MediaSession notification permission result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0A0A0C"))
        )
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)

        setContent {
            BACUFlixTheme {
                val uiState by homeViewModel.uiState.collectAsState()
                val favoriteKeys by homeViewModel.favoriteKeys.collectAsState()
                val downloads by homeViewModel.downloads.collectAsState()
                val activePlayerMovie = uiState.activePlayerMovie

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FlixColors.BgPrimary
                ) {
                    if (activePlayerMovie != null) {
                        val isPlayerMovieSaved = favoriteKeys.contains(activePlayerMovie.effectiveKey) ||
                            favoriteKeys.contains(activePlayerMovie.slug) ||
                            favoriteKeys.contains(activePlayerMovie.id)

                        val activeDownloadItem = downloads.firstOrNull {
                            it.movieKey == activePlayerMovie.effectiveKey && it.episodeIndex == activePlayerMovie.currentEpisodeIndex
                        }
                        val isPlayerMovieDownloaded = activeDownloadItem?.status == com.bacuflix.app.download.DownloadStatus.COMPLETED
                        val isPlayerMovieDownloading = activeDownloadItem?.status == com.bacuflix.app.download.DownloadStatus.DOWNLOADING ||
                            activeDownloadItem?.status == com.bacuflix.app.download.DownloadStatus.QUEUED ||
                            activeDownloadItem?.status == com.bacuflix.app.download.DownloadStatus.REMUXING

                        PlayerScreen(
                            initialMovie = activePlayerMovie,
                            isFavorite = isPlayerMovieSaved,
                            isDownloaded = isPlayerMovieDownloaded,
                            isDownloading = isPlayerMovieDownloading,
                            downloadProgress = activeDownloadItem?.progressPercent ?: 0,
                            onToggleFavorite = { movie ->
                                homeViewModel.toggleFavorite(movie)
                            },
                            onStartDownload = { movie ->
                                homeViewModel.startDownload(movie)
                            },
                            onCancelDownload = { id ->
                                homeViewModel.cancelDownload(id)
                            },
                            onGetEpisodeResume = { slug, epIdx ->
                                homeViewModel.getEpisodeResumeInfo(slug, epIdx)
                            },
                            onSaveProgress = { movie, curSec, durSec ->
                                homeViewModel.saveWatchProgress(movie, curSec, durSec)
                            },
                            onRecordPlayInteraction = { movie ->
                                homeViewModel.mlRecommendationService.recordMovieInteraction(movie, "play")
                            },
                            onShowToast = { msg ->
                                homeViewModel.showToast(msg)
                            },
                            onBackToDetail = {
                                homeViewModel.exitPlayer(returnToDetail = true)
                            },
                            onReturnHome = {
                                homeViewModel.exitPlayer(returnToDetail = false)
                            }
                        )
                    } else {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onExitApp = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val (movieSlug, epParam) = UrlUtils.parseDeepLink(data)
        if (!movieSlug.isNullOrBlank()) {
            homeViewModel.handleDeepLink(movieSlug, epParam)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
}
