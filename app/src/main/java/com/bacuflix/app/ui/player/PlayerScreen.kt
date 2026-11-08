package com.bacuflix.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bacuflix.app.model.EpisodeResumeInfo
import com.bacuflix.app.model.EpisodeServer
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.SubtitleCue
import com.bacuflix.app.player.PlayerController
import com.bacuflix.app.player.SeekController
import com.bacuflix.app.player.SubtitleParser
import com.bacuflix.app.ui.theme.FlixColors
import com.bacuflix.app.util.FormatTime
import com.bacuflix.app.util.UrlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

private val AppleGlassSurface = Color(0xB81C1C1E)
private val AppleGlassActive = Color(0xD92C2C2E)
private val AppleGlassBorder = Color(0x33FFFFFF)
private val AppleGlassHighlightBorder = Color(0x8CFFFFFF)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    initialMovie: Movie,
    onGetEpisodeResume: (slug: String, epIndex: Int) -> EpisodeResumeInfo?,
    onSaveProgress: (movie: Movie, currentSec: Int, durationSec: Int) -> Unit,
    onRecordPlayInteraction: (movie: Movie) -> Unit,
    onShowToast: (String) -> Unit,
    onBackToDetail: () -> Unit,
    onReturnHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    var currentMovie by remember(initialMovie.id, initialMovie.currentEpisodeIndex) { mutableStateOf(initialMovie) }
    val playbackState by PlayerController.uiState.collectAsState()

    val isEmbedOnly = remember(currentMovie.m3u8, currentMovie.fallbackEmbed) {
        val m3u8 = currentMovie.m3u8.trim()
        val isNativePlayable = m3u8.contains(".m3u8", ignoreCase = true) ||
            m3u8.contains("streamc.xyz", ignoreCase = true) ||
            m3u8.contains("streamvsmov.com", ignoreCase = true)
        (!isNativePlayable || m3u8.isEmpty()) &&
            currentMovie.fallbackEmbed.isNotBlank()
    }
    var forceFallbackEmbed by remember(currentMovie.id, currentMovie.currentEpisodeIndex) { mutableStateOf(false) }
    val useWebViewEmbed = isEmbedOnly || forceFallbackEmbed

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTick by remember { mutableIntStateOf(0) }
    var isFullscreenLandscape by remember { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }

    // Screen lock state
    var isScreenLocked by remember { mutableStateOf(false) }

    // Aspect ratio resize mode
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Playback speed & long-press 2.0x boost state
    var normalPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLongPressBoostActive by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }

    // In-player Episode & Server drawer state
    var isEpisodeDrawerOpen by remember { mutableStateOf(false) }
    var drawerEpisodeRangeIdx by remember { mutableIntStateOf(0) }

    // Sleep timer state
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }
    var sleepStopAtEpisodeEnd by remember { mutableStateOf(false) }

    // Skip Intro state
    var skipIntroDismissed by remember(currentMovie.id, currentMovie.currentEpisodeIndex) { mutableStateOf(false) }

    // Seek & timeline state
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragFraction by remember { mutableFloatStateOf(0f) }

    // Double-tap seek feedback (-10s left / +10s right)
    var leftSeekCumulativeSec by remember { mutableIntStateOf(0) }
    var rightSeekCumulativeSec by remember { mutableIntStateOf(0) }

    // Subtitles state
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var activeSubtitleText by remember { mutableStateOf("") }

    // Volume HUD state
    var playerVolume by remember { mutableFloatStateOf(1f) }
    var volumeHudVisible by remember { mutableStateOf(false) }
    var volumeHudTick by remember { mutableIntStateOf(0) }

    // Brightness HUD state
    var screenBrightness by remember {
        val currentAttr = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (currentAttr in 0.05f..1.0f) currentAttr else 0.7f)
    }
    var brightnessHudVisible by remember { mutableStateOf(false) }
    var brightnessHudTick by remember { mutableIntStateOf(0) }

    // Auto-next episode countdown state
    var autoNextRemainingSec by remember { mutableIntStateOf(0) }
    var autoNextCancelled by remember(currentMovie.id, currentMovie.currentEpisodeIndex) { mutableStateOf(false) }

    val episodes = currentMovie.episodesList
    val servers = currentMovie.serversList
    val currentEpIdx = currentMovie.currentEpisodeIndex
    val hasPrevEpisode = episodes.isNotEmpty() && currentEpIdx > 0
    val hasNextEpisode = episodes.isNotEmpty() && currentEpIdx < episodes.lastIndex
    var lastSavedSec by remember(currentMovie.id, currentMovie.currentEpisodeIndex) { mutableIntStateOf(-1) }

    fun saveCurrentProgress(force: Boolean = false) {
        if (useWebViewEmbed) return
        val curSec = (positionMs / 1000L).toInt()
        val durSec = (durationMs / 1000L).toInt()
        if (curSec > 2 && (force || kotlin.math.abs(curSec - lastSavedSec) >= 5)) {
            lastSavedSec = curSec
            try {
                onSaveProgress(currentMovie, curSec, durSec)
            } catch (_: Exception) {}
        }
    }

    fun switchToEpisode(targetIndex: Int) {
        if (targetIndex !in episodes.indices) return
        saveCurrentProgress(force = true)
        val ep = episodes[targetIndex]
        val m3u8 = ep.m3u8.trim()
        val embed = ep.fallbackEmbed.trim()
        val seriesTitle = currentMovie.seriesTitle.ifBlank {
            currentMovie.title.substringBefore(" - ")
        }
        val epName = ep.name.ifBlank { "Tập ${targetIndex + 1}" }
        val cleanSlug = com.bacuflix.app.data.FavoritesRepository.sanitizeApiSlug(currentMovie.slug)
        val baseKey = com.bacuflix.app.data.FavoritesRepository.normalizeMovieKey(cleanSlug, currentMovie.id.ifBlank { currentMovie.effectiveKey })
        val savedResume = onGetEpisodeResume(baseKey, targetIndex)?.currentTime ?: 0

        forceFallbackEmbed = false
        autoNextRemainingSec = 0
        autoNextCancelled = false
        skipIntroDismissed = false
        isEpisodeDrawerOpen = false
        currentMovie = currentMovie.copy(
            id = baseKey,
            slug = cleanSlug,
            title = "$seriesTitle - $epName",
            seriesTitle = seriesTitle,
            episodeName = epName,
            currentEpisodeIndex = targetIndex,
            m3u8 = m3u8.ifBlank { embed },
            fallbackEmbed = embed,
            resumeTime = savedResume
        )
    }

    fun switchToServer(targetServer: EpisodeServer) {
        if (targetServer.episodes.isEmpty()) return
        saveCurrentProgress(force = true)
        val safeIdx = currentEpIdx.coerceIn(0, targetServer.episodes.lastIndex)
        val ep = targetServer.episodes[safeIdx]
        val m3u8 = ep.m3u8.trim()
        val embed = ep.fallbackEmbed.trim()
        val seriesTitle = currentMovie.seriesTitle.ifBlank {
            currentMovie.title.substringBefore(" - ")
        }
        val epName = ep.name.ifBlank { "Tập ${safeIdx + 1}" }
        val curSec = (positionMs / 1000L).toInt().coerceAtLeast(0)

        forceFallbackEmbed = false
        isEpisodeDrawerOpen = false
        currentMovie = currentMovie.copy(
            title = "$seriesTitle - $epName",
            seriesTitle = seriesTitle,
            episodeName = epName,
            serverName = targetServer.serverName,
            source = targetServer.serverName,
            episodesList = targetServer.episodes,
            currentEpisodeIndex = safeIdx,
            m3u8 = m3u8.ifBlank { embed },
            fallbackEmbed = embed,
            resumeTime = curSec
        )
        onShowToast("Đã chuyển sang nguồn phát: ${targetServer.serverName}")
    }

    fun performRelativeSeek(deltaSeconds: Int) {
        try {
            val player = PlayerController.getOrCreatePlayer(context)
            val curSec = player.currentPosition / 1000.0
            val durSec = if (player.duration > 0) player.duration / 1000.0 else Double.NaN
            val targetSec = SeekController.computeRelativeSeekTarget(curSec, durSec, deltaSeconds.toDouble())
            val targetMs = (targetSec * 1000.0).toLong().coerceAtLeast(0L)
            player.seekTo(targetMs)
            positionMs = targetMs
        } catch (_: Exception) {}
    }

    fun adjustVolume(delta: Float) {
        try {
            val player = PlayerController.getOrCreatePlayer(context)
            val nextVol = (playerVolume + delta).coerceIn(0f, 1f)
            playerVolume = nextVol
            player.volume = nextVol
            volumeHudVisible = true
            volumeHudTick++
            lastInteractionTick++
        } catch (_: Exception) {}
    }

    fun adjustBrightness(delta: Float) {
        val act = activity ?: return
        try {
            val nextBright = (screenBrightness + delta).coerceIn(0.05f, 1.0f)
            screenBrightness = nextBright
            val lp = act.window.attributes
            lp.screenBrightness = nextBright
            act.window.attributes = lp
            brightnessHudVisible = true
            brightnessHudTick++
            lastInteractionTick++
        } catch (_: Exception) {}
    }

    fun applyPlaybackSpeed(speed: Float) {
        normalPlaybackSpeed = speed
        if (!isLongPressBoostActive) {
            PlayerController.setPlaybackSpeed(context, speed)
        }
        onShowToast("Tốc độ phát: ${speed}x")
    }

    fun cycleAspectRatio() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerViewRef?.resizeMode = resizeMode
        val modeLabel = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Phóng đầy màn hình (Zoom)"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Kéo giãn toàn màn (Fill)"
            else -> "Vừa khung hình chuẩn (Fit)"
        }
        onShowToast("Khung hình: $modeLabel")
    }

    fun enterPipMode() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                controlsVisible = false
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                act.enterPictureInPictureMode(params)
                isInPipMode = true
            } catch (_: Exception) {
                onShowToast("Thiết bị không hỗ trợ chế độ cửa sổ nổi PiP.")
            }
        } else {
            onShowToast("Cần Android 8.0+ để dùng Picture-in-Picture.")
        }
    }

    fun hidePlayerSystemBars() {
        val act = activity ?: return
        try {
            val window = act.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        } catch (_: Exception) {}
    }

    fun restoreSystemBars() {
        val act = activity ?: return
        try {
            val window = act.window
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        } catch (_: Exception) {}
    }

    fun toggleFullscreenOrientation() {
        val act = activity ?: return
        isFullscreenLandscape = !isFullscreenLandscape
        try {
            act.requestedOrientation = if (isFullscreenLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            hidePlayerSystemBars()
        } catch (_: Exception) {}
    }

    // Ensure status bar remains hidden throughout movie playback
    LaunchedEffect(currentMovie.id, isFullscreenLandscape, controlsVisible) {
        hidePlayerSystemBars()
    }

    BackHandler {
        when {
            isScreenLocked -> {
                controlsVisible = true
                lastInteractionTick++
                onShowToast("Màn hình đang khóa — nhấn nút 'Mở khóa' để thao tác")
            }
            isEpisodeDrawerOpen -> isEpisodeDrawerOpen = false
            showSpeedPicker -> showSpeedPicker = false
            showSleepTimerMenu -> showSleepTimerMenu = false
            else -> {
                saveCurrentProgress(force = true)
                if (isFullscreenLandscape) {
                    toggleFullscreenOrientation()
                }
                restoreSystemBars()
                PlayerController.stopAndRelease(context)
                onBackToDetail()
            }
        }
    }

    // Keep screen awake during foreground playback, handle PiP & background audio transition, & cleanup on exit
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else false
            isInPipMode = inPip

            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    saveCurrentProgress(force = true)
                    if (activity?.isChangingConfigurations != true && !inPip) {
                        playerViewRef?.player = null
                        PlayerController.setBackgroundMode(true)
                    }
                }
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    hidePlayerSystemBars()
                    PlayerController.setBackgroundMode(false)
                    try {
                        val targetPlayer = PlayerController.getOrCreatePlayer(context)
                        if (playerViewRef?.player !== targetPlayer) {
                            playerViewRef?.player = targetPlayer
                        }
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        try {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hidePlayerSystemBars()
        } catch (_: Exception) {}
        onDispose {
            saveCurrentProgress(force = true)
            playerViewRef?.player = null
            playerViewRef = null
            try {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity?.let { act ->
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                restoreSystemBars()
            } catch (_: Exception) {}
            PlayerController.onNextEpisodeCallback = null
            PlayerController.onPreviousEpisodeCallback = null
            PlayerController.stopAndRelease(context)
        }
    }

    val currentSwitchToEpisode by rememberUpdatedState(::switchToEpisode)

    // Register MediaSession next/prev episode callbacks
    LaunchedEffect(currentMovie.id, currentEpIdx, episodes.size) {
        PlayerController.onNextEpisodeCallback = if (hasNextEpisode) {
            { currentSwitchToEpisode(currentEpIdx + 1) }
        } else null
        PlayerController.onPreviousEpisodeCallback = if (hasPrevEpisode) {
            { currentSwitchToEpisode(currentEpIdx - 1) }
        } else null
    }

    // Start playback when currentMovie or episode changes
    LaunchedEffect(currentMovie.id, currentMovie.currentEpisodeIndex, currentMovie.m3u8, useWebViewEmbed) {
        try {
            onRecordPlayInteraction(currentMovie)
        } catch (_: Exception) {}
        if (!useWebViewEmbed) {
            val startMs = (currentMovie.resumeTime.coerceAtLeast(0)) * 1000L
            val rawStream = currentMovie.m3u8.trim()
            val resolvedStream = when {
                rawStream.contains("streamc.xyz", ignoreCase = true) && rawStream.contains("hash=") -> {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.bacuflix.app.data.KkphimApiService.resolveStreamcBootstrapSync(rawStream)
                    }
                }
                rawStream.contains("streamvsmov.com/video/", ignoreCase = true) -> {
                    com.bacuflix.app.data.KkphimApiService.extractDirectM3u8FromEmbed(rawStream)
                }
                else -> rawStream
            }
            if (resolvedStream.isNullOrBlank()) {
                // If StreamC bootstrap failed (e.g. Turnstile enabled), fall back to WebView with Referer
                forceFallbackEmbed = true
                return@LaunchedEffect
            }
            val movieToPlay = if (resolvedStream != rawStream) {
                currentMovie.copy(m3u8 = resolvedStream)
            } else {
                currentMovie
            }
            PlayerController.startPlayback(context, movieToPlay, startMs)
            PlayerController.setPlaybackSpeed(context, normalPlaybackSpeed)
            if (currentMovie.resumeTime > 5) {
                onShowToast("Tiếp tục phát từ ${FormatTime.formatTime(currentMovie.resumeTime.toDouble())}")
            }
        }
        // Load external subtitle if provided
        if (!currentMovie.subtitle.isNullOrBlank()) {
            try {
                subtitleCues = SubtitleParser.loadSubtitleFromUrl(currentMovie.subtitle)
            } catch (_: Exception) {
                subtitleCues = emptyList()
            }
        } else {
            subtitleCues = emptyList()
        }
    }

    // Poll ExoPlayer position & update subtitles every 250ms, save progress every 10s
    LaunchedEffect(useWebViewEmbed, currentMovie.id, currentMovie.currentEpisodeIndex, subtitlesEnabled, subtitleCues) {
        if (useWebViewEmbed) return@LaunchedEffect
        var tickCount = 0
        while (isActive) {
            try {
                val player = PlayerController.getOrCreatePlayer(context)
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.coerceAtLeast(0L)
                positionMs = pos
                if (dur > 0L) durationMs = dur

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    isInPipMode = activity?.isInPictureInPictureMode == true
                }

                if (subtitlesEnabled && subtitleCues.isNotEmpty()) {
                    activeSubtitleText = SubtitleParser.getActiveSubtitleText(subtitleCues, pos / 1000.0)
                } else {
                    activeSubtitleText = ""
                }

                tickCount++
                if (tickCount % 40 == 0 && player.isPlaying) {
                    saveCurrentProgress()
                }
            } catch (_: Exception) {}
            delay(250L)
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerRemainingSec) {
        if (sleepTimerRemainingSec > 0) {
            delay(1000L)
            val next = sleepTimerRemainingSec - 1
            sleepTimerRemainingSec = next
            if (next == 0) {
                try {
                    val player = PlayerController.getOrCreatePlayer(context)
                    player.pause()
                    saveCurrentProgress(force = true)
                    onShowToast("Đã tạm dừng phim theo hẹn giờ ngủ")
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-hide controls after 4.5s while playing
    LaunchedEffect(controlsVisible, lastInteractionTick, playbackState.isPlaying, isEpisodeDrawerOpen, showSpeedPicker, showSleepTimerMenu) {
        if (controlsVisible && playbackState.isPlaying && autoNextRemainingSec == 0 && !isEpisodeDrawerOpen && !showSpeedPicker && !showSleepTimerMenu) {
            delay(4500L)
            controlsVisible = false
        }
    }

    // Auto-hide volume & brightness HUDs after 1.6s
    LaunchedEffect(volumeHudVisible, volumeHudTick) {
        if (volumeHudVisible) {
            delay(1600L)
            volumeHudVisible = false
        }
    }
    LaunchedEffect(brightnessHudVisible, brightnessHudTick) {
        if (brightnessHudVisible) {
            delay(1600L)
            brightnessHudVisible = false
        }
    }

    // Clear double-tap seek indicators after 850ms
    LaunchedEffect(leftSeekCumulativeSec) {
        if (leftSeekCumulativeSec > 0) {
            delay(850L)
            leftSeekCumulativeSec = 0
        }
    }
    LaunchedEffect(rightSeekCumulativeSec) {
        if (rightSeekCumulativeSec > 0) {
            delay(850L)
            rightSeekCumulativeSec = 0
        }
    }

    // Trigger 5-second Auto-Next Episode countdown when playback ends (unless Sleep Timer "Hết tập" is active)
    LaunchedEffect(playbackState.isEnded, hasNextEpisode, autoNextCancelled, sleepStopAtEpisodeEnd) {
        if (playbackState.isEnded) {
            if (sleepStopAtEpisodeEnd) {
                sleepStopAtEpisodeEnd = false
                autoNextRemainingSec = 0
                saveCurrentProgress(force = true)
                onShowToast("Đã dừng phát khi hết tập theo hẹn giờ ngủ")
            } else if (hasNextEpisode && !autoNextCancelled) {
                for (sec in 5 downTo 1) {
                    autoNextRemainingSec = sec
                    delay(1000L)
                }
                autoNextRemainingSec = 0
                switchToEpisode(currentEpIdx + 1)
            } else {
                autoNextRemainingSec = 0
            }
        } else {
            autoNextRemainingSec = 0
        }
    }

    val showSkipIntroButton = !useWebViewEmbed &&
        !isInPipMode &&
        !isScreenLocked &&
        !skipIntroDismissed &&
        positionMs in 15_000L..145_000L &&
        durationMs > 600_000L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (useWebViewEmbed) {
            FallbackEmbedWebView(
                embedUrl = currentMovie.fallbackEmbed.ifBlank { currentMovie.m3u8 },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Native AndroidX Media3 ExoPlayer surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.resizeMode = resizeMode
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = PlayerController.getOrCreatePlayer(ctx)
                        playerViewRef = this
                    }
                },
                update = { view ->
                    playerViewRef = view
                    view.resizeMode = resizeMode
                    val targetPlayer = PlayerController.getOrCreatePlayer(context)
                    if (view.player !== targetPlayer) {
                        view.player = targetPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Left / Center / Right Gesture Zones for Single-Tap, Double-Tap 10s Seek, Long-Press 2x Speed, & Vertical Swipe Brightness/Volume
            if (!isInPipMode) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left 35%: Double-tap to rewind 10s, Long-press 2x speed, Vertical drag for Brightness
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .pointerInput(isScreenLocked, normalPlaybackSpeed) {
                                detectTapGestures(
                                    onTap = {
                                        controlsVisible = !controlsVisible
                                        lastInteractionTick++
                                    },
                                    onDoubleTap = {
                                        if (!isScreenLocked) {
                                            performRelativeSeek(-10)
                                            leftSeekCumulativeSec += 10
                                            lastInteractionTick++
                                        }
                                    },
                                    onLongPress = {
                                        if (!isScreenLocked) {
                                            isLongPressBoostActive = true
                                            PlayerController.setPlaybackSpeed(context, 2.0f)
                                        }
                                    },
                                    onPress = {
                                        tryAwaitRelease()
                                        if (isLongPressBoostActive) {
                                            isLongPressBoostActive = false
                                            PlayerController.setPlaybackSpeed(context, normalPlaybackSpeed)
                                        }
                                    }
                                )
                            }
                            .pointerInput(isScreenLocked) {
                                if (!isScreenLocked) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val delta = (-dragAmount / size.height.coerceAtLeast(1)) * 1.15f
                                        adjustBrightness(delta)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = leftSeekCumulativeSec > 0,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            SeekRippleBadge(
                                isRewind = true,
                                seconds = leftSeekCumulativeSec
                            )
                        }
                    }

                    // Center 30%: Single-tap to toggle controls, Double-tap to Play/Pause, Long-press 2x speed
                    Box(
                        modifier = Modifier
                            .weight(0.30f)
                            .fillMaxHeight()
                            .pointerInput(isScreenLocked, normalPlaybackSpeed) {
                                detectTapGestures(
                                    onTap = {
                                        controlsVisible = !controlsVisible
                                        lastInteractionTick++
                                    },
                                    onDoubleTap = {
                                        if (!isScreenLocked) {
                                            val player = PlayerController.getOrCreatePlayer(context)
                                            if (player.isPlaying) {
                                                player.pause()
                                                saveCurrentProgress()
                                            } else {
                                                player.play()
                                            }
                                            controlsVisible = true
                                            lastInteractionTick++
                                        }
                                    },
                                    onLongPress = {
                                        if (!isScreenLocked) {
                                            isLongPressBoostActive = true
                                            PlayerController.setPlaybackSpeed(context, 2.0f)
                                        }
                                    },
                                    onPress = {
                                        tryAwaitRelease()
                                        if (isLongPressBoostActive) {
                                            isLongPressBoostActive = false
                                            PlayerController.setPlaybackSpeed(context, normalPlaybackSpeed)
                                        }
                                    }
                                )
                            }
                    )

                    // Right 35%: Double-tap to forward 10s, Long-press 2x speed, Vertical drag for Volume
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .pointerInput(isScreenLocked, normalPlaybackSpeed) {
                                detectTapGestures(
                                    onTap = {
                                        controlsVisible = !controlsVisible
                                        lastInteractionTick++
                                    },
                                    onDoubleTap = {
                                        if (!isScreenLocked) {
                                            performRelativeSeek(10)
                                            rightSeekCumulativeSec += 10
                                            lastInteractionTick++
                                        }
                                    },
                                    onLongPress = {
                                        if (!isScreenLocked) {
                                            isLongPressBoostActive = true
                                            PlayerController.setPlaybackSpeed(context, 2.0f)
                                        }
                                    },
                                    onPress = {
                                        tryAwaitRelease()
                                        if (isLongPressBoostActive) {
                                            isLongPressBoostActive = false
                                            PlayerController.setPlaybackSpeed(context, normalPlaybackSpeed)
                                        }
                                    }
                                )
                            }
                            .pointerInput(isScreenLocked) {
                                if (!isScreenLocked) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val delta = (-dragAmount / size.height.coerceAtLeast(1)) * 1.15f
                                        adjustVolume(delta)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = rightSeekCumulativeSec > 0,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            SeekRippleBadge(
                                isRewind = false,
                                seconds = rightSeekCumulativeSec
                            )
                        }
                    }
                }
            }
        }

        // Long-Press 2.0x Speed Boost Badge Overlay (Apple TV Pill)
        androidx.compose.animation.AnimatedVisibility(
            visible = isLongPressBoostActive && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(AppleGlassActive, RoundedCornerShape(999.dp))
                    .border(1.dp, AppleGlassHighlightBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Tua nhanh 2.0x",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // External Subtitle Overlay
        if (!useWebViewEmbed && subtitlesEnabled && activeSubtitleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (controlsVisible && !isScreenLocked && !isInPipMode) 152.dp else 32.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
                    .background(Color(0xBF000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeSubtitleText,
                    color = Color.White,
                    fontSize = if (isInPipMode) 11.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Brightness HUD Overlay (Top-Left Apple TV Frosted Pill)
        androidx.compose.animation.AnimatedVisibility(
            visible = brightnessHudVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 74.dp, start = 24.dp)
        ) {
            val pct = (screenBrightness * 100f).roundToInt().coerceIn(5, 100)
            Row(
                modifier = Modifier
                    .background(AppleGlassActive, RoundedCornerShape(999.dp))
                    .border(1.dp, AppleGlassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = "Brightness",
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(18.dp)
                )
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(screenBrightness.coerceIn(0f, 1f))
                            .background(Color.White)
                    )
                }
                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Volume HUD Overlay (Top-Right Apple TV Frosted Pill)
        androidx.compose.animation.AnimatedVisibility(
            visible = volumeHudVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 74.dp, end = 24.dp)
        ) {
            val pct = (playerVolume * 100f).roundToInt()
            Row(
                modifier = Modifier
                    .background(AppleGlassActive, RoundedCornerShape(999.dp))
                    .border(1.dp, AppleGlassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (pct == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(playerVolume.coerceIn(0f, 1f))
                            .background(Color.White)
                    )
                }
                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Buffering Spinner / Error Overlay
        if (!useWebViewEmbed && (playbackState.isBuffering || playbackState.errorMessage != null)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (playbackState.errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .background(AppleGlassActive, RoundedCornerShape(18.dp))
                            .border(1.dp, AppleGlassBorder, RoundedCornerShape(18.dp))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = playbackState.errorMessage ?: "Lỗi phát video",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val resumeMs = positionMs.coerceAtLeast(0L)
                                    PlayerController.startPlayback(context, currentMovie, resumeMs)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FlixColors.Red)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Thử lại", fontWeight = FontWeight.Bold)
                            }
                            if (servers.size > 1) {
                                OutlinedButton(
                                    onClick = { isEpisodeDrawerOpen = true }
                                ) {
                                    Text("Đổi Server (${servers.size})", color = Color.White)
                                }
                            }
                            if (currentMovie.fallbackEmbed.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        PlayerController.stopAndRelease(context)
                                        forceFallbackEmbed = true
                                    }
                                ) {
                                    Text("Trình phát dự phòng", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(if (isInPipMode) 28.dp else 46.dp)
                        )
                        if (!isInPipMode) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Đang tải luồng phát...",
                                color = Color(0xD9FFFFFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Skip Intro (+85s) Floating Apple TV Capsule Button
        androidx.compose.animation.AnimatedVisibility(
            visible = showSkipIntroButton && autoNextRemainingSec == 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (controlsVisible) 146.dp else 36.dp, end = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xEBFFFFFF))
                    .border(1.dp, Color.White, RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            performRelativeSeek(85)
                            skipIntroDismissed = true
                            onShowToast("Đã bỏ qua phần mở đầu (+85s)")
                        }
                        .padding(start = 16.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Bỏ qua Mở đầu",
                        color = Color.Black,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable { skipIntroDismissed = true }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color(0x99000000),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 5-Second Auto-Next Episode Countdown Overlay (Apple TV Style)
        androidx.compose.animation.AnimatedVisibility(
            visible = autoNextRemainingSec > 0 && hasNextEpisode && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 144.dp, end = 24.dp)
        ) {
            val nextEp = episodes.getOrNull(currentEpIdx + 1)
            val nextEpTitle = nextEp?.name?.ifBlank { "Tập ${currentEpIdx + 2}" } ?: "Tập tiếp theo"
            Column(
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 340.dp)
                    .background(AppleGlassActive, RoundedCornerShape(18.dp))
                    .border(1.dp, AppleGlassHighlightBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "TIẾP THEO · ${autoNextRemainingSec}S",
                    color = Color(0xB3EBEBF5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${currentMovie.seriesTitle.ifBlank { currentMovie.title }} · $nextEpTitle",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { switchToEpisode(currentEpIdx + 1) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Phát ngay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            autoNextCancelled = true
                            autoNextRemainingSec = 0
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text("Hủy", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Locked Screen Floating Unlock Pill
        androidx.compose.animation.AnimatedVisibility(
            visible = isScreenLocked && controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Surface(
                color = AppleGlassActive,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .border(1.2.dp, AppleGlassHighlightBorder, RoundedCornerShape(999.dp))
                    .clickable {
                        isScreenLocked = false
                        controlsVisible = true
                        lastInteractionTick++
                        onShowToast("Đã mở khóa màn hình cảm ứng")
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Mở khóa",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Chạm để mở khóa màn hình",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // =========================================================================
        // APPLE TV+ STYLE PLAYER CONTROLS OVERLAY (Top Bar + Center Transport + Bottom Scrubber)
        // =========================================================================
        androidx.compose.animation.AnimatedVisibility(
            visible = (controlsVisible || useWebViewEmbed) && !isScreenLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // -----------------------------------------------------------------
                // 1. APPLE TV TOP FLOATING BAR
                // -----------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xD9000000), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top-Left Cluster: Close/Back, PiP, Background Audio
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppleCircleButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            onClick = {
                                saveCurrentProgress()
                                if (isFullscreenLandscape) toggleFullscreenOrientation()
                                PlayerController.stopAndRelease(context)
                                onBackToDetail()
                            }
                        )
                        if (!useWebViewEmbed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            AppleCircleButton(
                                icon = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Cửa sổ nổi PiP",
                                sizeDp = 40,
                                iconSizeDp = 17,
                                onClick = { enterPipMode() }
                            )
                        }
                        if (!useWebViewEmbed) {
                            AppleCircleButton(
                                icon = Icons.Default.Headphones,
                                contentDescription = "Phát trong nền",
                                sizeDp = 40,
                                iconSizeDp = 17,
                                onClick = {
                                    onShowToast("Đang phát trong nền — điều khiển từ màn hình khóa")
                                    activity?.moveTaskToBack(true)
                                }
                            )
                        }
                    }

                    // Top-Center Metadata Header (Series + Episode Title)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                            .clickable {
                                saveCurrentProgress()
                                if (isFullscreenLandscape) toggleFullscreenOrientation()
                                PlayerController.stopAndRelease(context)
                                onReturnHome()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = FlixColors.RedBright, fontWeight = FontWeight.Black)) {
                                        append("BACU")
                                    }
                                    withStyle(SpanStyle(color = Color(0xD9FFFFFF), fontWeight = FontWeight.Bold)) {
                                        append("Flix")
                                    }
                                },
                                fontSize = 11.sp
                            )
                            if (currentMovie.serverName.isNotBlank()) {
                                Text(
                                    text = "· ${currentMovie.serverName}",
                                    color = Color(0xA6EBEBF5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (sleepTimerRemainingSec > 0 || sleepStopAtEpisodeEnd) {
                                Text(
                                    text = if (sleepStopAtEpisodeEnd) "· 🌙 Hết tập" else "· 🌙 ${FormatTime.formatTime(sleepTimerRemainingSec.toDouble())}",
                                    color = Color(0xFFFACC15),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = currentMovie.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Top-Right Unified Apple TV Frosted Capsule (Lock, Share, Fullscreen)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppleGlassSurface)
                            .border(1.dp, AppleGlassBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!useWebViewEmbed) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        isScreenLocked = true
                                        showSpeedPicker = false
                                        showSleepTimerMenu = false
                                        isEpisodeDrawerOpen = false
                                        onShowToast("Đã khóa màn hình cảm ứng")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Khóa màn hình",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    shareEpisodeLink(
                                        context = context,
                                        movie = currentMovie,
                                        onShowToast = onShowToast
                                    )
                                    lastInteractionTick++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Chia sẻ",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    toggleFullscreenOrientation()
                                    lastInteractionTick++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFullscreenLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toàn màn hình",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // -----------------------------------------------------------------
                // 2. APPLE TV CENTER TRANSPORT OVERLAY (Prev · -10s · Play/Pause · +10s · Next)
                // -----------------------------------------------------------------
                if (!useWebViewEmbed && !playbackState.isBuffering && playbackState.errorMessage == null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Previous Episode Circle
                        AppleCircleButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Tập trước",
                            sizeDp = 46,
                            iconSizeDp = 22,
                            enabled = hasPrevEpisode,
                            onClick = { switchToEpisode(currentEpIdx - 1) }
                        )

                        // Rewind 10s Circle
                        AppleSeekCircleButton(
                            icon = Icons.Default.FastRewind,
                            badgeText = "10",
                            onClick = {
                                performRelativeSeek(-10)
                                lastInteractionTick++
                            }
                        )

                        // Hero Play / Pause Frosted Circle
                        Surface(
                            color = Color(0xD928282E),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(74.dp)
                                .border(1.5.dp, AppleGlassHighlightBorder, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    val player = PlayerController.getOrCreatePlayer(context)
                                    if (player.isPlaying) {
                                        player.pause()
                                        saveCurrentProgress()
                                    } else {
                                        if (playbackState.isEnded) {
                                            player.seekTo(0L)
                                        }
                                        player.play()
                                    }
                                    lastInteractionTick++
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Tạm dừng" else "Phát",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Forward 10s Circle
                        AppleSeekCircleButton(
                            icon = Icons.Default.FastForward,
                            badgeText = "10",
                            onClick = {
                                performRelativeSeek(10)
                                lastInteractionTick++
                            }
                        )

                        // Next Episode Circle
                        AppleCircleButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Tập tiếp",
                            sizeDp = 46,
                            iconSizeDp = 22,
                            enabled = hasNextEpisode,
                            onClick = { switchToEpisode(currentEpIdx + 1) }
                        )
                    }
                }

                // Speed Picker Popup Card (Apple TV Frosted Sheet)
                if (showSpeedPicker && !useWebViewEmbed) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 142.dp, end = 24.dp)
                            .background(AppleGlassActive, RoundedCornerShape(18.dp))
                            .border(1.dp, AppleGlassBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Tốc độ phát (Nhấn giữ màn hình để tua 2.0x)",
                            color = Color(0xB3EBEBF5),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PLAYBACK_SPEED_OPTIONS.forEach { spd ->
                                val active = kotlin.math.abs(normalPlaybackSpeed - spd) < 0.01f
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (active) Color.White else Color(0x33FFFFFF))
                                        .clickable {
                                            applyPlaybackSpeed(spd)
                                            showSpeedPicker = false
                                            lastInteractionTick++
                                        }
                                        .padding(horizontal = 13.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "${spd}x",
                                        color = if (active) Color.Black else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Sleep Timer Popup Card (Apple TV Frosted Sheet)
                if (showSleepTimerMenu && !useWebViewEmbed) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 142.dp, end = 24.dp)
                            .background(AppleGlassActive, RoundedCornerShape(18.dp))
                            .border(1.dp, AppleGlassBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Hẹn giờ tự động tạm dừng phim",
                            color = Color(0xB3EBEBF5),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val timerPresets = listOf(
                                "Tắt" to 0,
                                "15 phút" to 15 * 60,
                                "30 phút" to 30 * 60,
                                "60 phút" to 60 * 60,
                                "Hết tập này" to -1
                            )
                            timerPresets.forEach { (label, secValue) ->
                                val isSelected = when (secValue) {
                                    0 -> sleepTimerRemainingSec == 0 && !sleepStopAtEpisodeEnd
                                    -1 -> sleepStopAtEpisodeEnd
                                    else -> sleepTimerRemainingSec in (secValue - 60)..secValue
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isSelected) Color.White else Color(0x33FFFFFF))
                                        .clickable {
                                            when (secValue) {
                                                0 -> {
                                                    sleepTimerRemainingSec = 0
                                                    sleepStopAtEpisodeEnd = false
                                                    onShowToast("Đã tắt hẹn giờ ngủ")
                                                }
                                                -1 -> {
                                                    sleepTimerRemainingSec = 0
                                                    sleepStopAtEpisodeEnd = true
                                                    onShowToast("Hẹn giờ: Tự dừng khi hết tập hiện tại")
                                                }
                                                else -> {
                                                    sleepStopAtEpisodeEnd = false
                                                    sleepTimerRemainingSec = secValue
                                                    onShowToast("Đã hẹn giờ tắt sau $label")
                                                }
                                            }
                                            showSleepTimerMenu = false
                                            lastInteractionTick++
                                        }
                                        .padding(horizontal = 13.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // -----------------------------------------------------------------
                // 3. APPLE TV BOTTOM SCRUBBER & FROSTED PILL BAR
                // -----------------------------------------------------------------
                if (!useWebViewEmbed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xEB000000))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        val curSec = (positionMs / 1000L).toDouble()
                        val durSec = (durationMs / 1000L).toDouble()
                        val progressFraction = if (isDraggingSlider) {
                            sliderDragFraction
                        } else if (durationMs > 0L) {
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val displayedCurSec = if (isDraggingSlider && durSec > 0) sliderDragFraction * durSec else curSec
                        val remainingSec = (durSec - displayedCurSec).coerceAtLeast(0.0)

                        // Apple TV Scrubber Bar with Elapsed & Remaining Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = FormatTime.formatTime(displayedCurSec),
                                color = Color(0xE6FFFFFF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = progressFraction,
                                onValueChange = { value ->
                                    isDraggingSlider = true
                                    sliderDragFraction = value.coerceIn(0f, 1f)
                                    lastInteractionTick++
                                },
                                onValueChangeFinished = {
                                    if (durationMs > 0L) {
                                        val targetMs = (sliderDragFraction * durationMs).toLong().coerceIn(0L, durationMs)
                                        PlayerController.getOrCreatePlayer(context).seekTo(targetMs)
                                        positionMs = targetMs
                                    }
                                    isDraggingSlider = false
                                    lastInteractionTick++
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color(0x45FFFFFF)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (durSec > 0) "-${FormatTime.formatTime(remainingSec)}" else "00:00",
                                color = Color(0xB3EBEBF5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Apple TV Bottom Frosted Pills Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Episode & Server Pill Group
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (episodes.size > 1 || servers.size > 1) {
                                    AppleControlPill(
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        label = "Tập ${currentEpIdx + 1}/${episodes.size.coerceAtLeast(1)} · Server",
                                        isActive = isEpisodeDrawerOpen,
                                        onClick = {
                                            isEpisodeDrawerOpen = !isEpisodeDrawerOpen
                                            showSpeedPicker = false
                                            showSleepTimerMenu = false
                                            lastInteractionTick++
                                        }
                                    )
                                }
                                if (hasNextEpisode) {
                                    AppleControlPill(
                                        icon = Icons.Default.SkipNext,
                                        label = "Tập tiếp",
                                        onClick = { switchToEpisode(currentEpIdx + 1) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Right Settings & Audio/Video Pill Group
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppleControlPill(
                                    icon = Icons.Default.ClosedCaption,
                                    label = if (subtitlesEnabled) "Phụ đề: Bật" else "Phụ đề: Tắt",
                                    isActive = subtitlesEnabled && subtitleCues.isNotEmpty(),
                                    onClick = {
                                        subtitlesEnabled = !subtitlesEnabled
                                        onShowToast(if (subtitlesEnabled) "Đã bật phụ đề" else "Đã tắt phụ đề")
                                        lastInteractionTick++
                                    }
                                )
                                AppleControlPill(
                                    icon = Icons.Default.Speed,
                                    label = "${normalPlaybackSpeed}x",
                                    isActive = kotlin.math.abs(normalPlaybackSpeed - 1.0f) > 0.01f || showSpeedPicker,
                                    onClick = {
                                        showSpeedPicker = !showSpeedPicker
                                        showSleepTimerMenu = false
                                        isEpisodeDrawerOpen = false
                                        lastInteractionTick++
                                    }
                                )
                                AppleControlPill(
                                    icon = Icons.Default.AspectRatio,
                                    label = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Khung hình: Zoom"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Khung hình: Fill"
                                        else -> "Khung hình: Fit"
                                    },
                                    isActive = resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                    onClick = {
                                        cycleAspectRatio()
                                        lastInteractionTick++
                                    }
                                )
                                AppleControlPill(
                                    icon = Icons.Default.Bedtime,
                                    label = if (sleepStopAtEpisodeEnd) "Hết tập" else if (sleepTimerRemainingSec > 0) "${(sleepTimerRemainingSec / 60).coerceAtLeast(1)}p" else "Hẹn giờ",
                                    isActive = sleepTimerRemainingSec > 0 || sleepStopAtEpisodeEnd || showSleepTimerMenu,
                                    onClick = {
                                        showSleepTimerMenu = !showSleepTimerMenu
                                        showSpeedPicker = false
                                        isEpisodeDrawerOpen = false
                                        lastInteractionTick++
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // In-Player Episode & Multi-Server Drawer Modal
        androidx.compose.animation.AnimatedVisibility(
            visible = isEpisodeDrawerOpen && !isScreenLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000))
                    .clickable { isEpisodeDrawerOpen = false },
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 440.dp)
                        .fillMaxHeight()
                        .background(Color(0xFA141418))
                        .border(1.dp, AppleGlassBorder)
                        .clickable(enabled = false) {}
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chọn Server & Tập phim",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppleGlassSurface)
                                .clickable { isEpisodeDrawerOpen = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Server Switcher inside Player
                    if (servers.size > 1) {
                        Text(
                            text = "Nguồn phát (Server):",
                            color = Color(0xB3EBEBF5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            servers.forEach { srv ->
                                val isCurrentSrv = srv.serverName == currentMovie.serverName
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isCurrentSrv) Color.White else AppleGlassSurface)
                                        .border(1.dp, if (isCurrentSrv) Color.White else AppleGlassBorder, RoundedCornerShape(999.dp))
                                        .clickable { switchToServer(srv) }
                                        .padding(horizontal = 13.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = srv.serverName,
                                        color = if (isCurrentSrv) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Range Chunk Tabs for Long Series (> 40 episodes)
                    val chunkSize = 40
                    val totalChunks = ((episodes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
                    if (totalChunks > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (chunkIdx in 0 until totalChunks) {
                                val startNum = chunkIdx * chunkSize + 1
                                val endNum = ((chunkIdx + 1) * chunkSize).coerceAtMost(episodes.size)
                                val selected = drawerEpisodeRangeIdx.coerceIn(0, totalChunks - 1) == chunkIdx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (selected) Color(0xFF2C2C34) else AppleGlassSurface)
                                        .border(
                                            1.dp,
                                            if (selected) Color.White else Color.Transparent,
                                            RoundedCornerShape(999.dp)
                                        )
                                        .clickable { drawerEpisodeRangeIdx = chunkIdx }
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Tập $startNum-$endNum",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    val activeChunk = drawerEpisodeRangeIdx.coerceIn(0, totalChunks - 1)
                    val startEpIndex = if (totalChunks > 1) activeChunk * chunkSize else 0
                    val endEpIndex = if (totalChunks > 1) ((activeChunk + 1) * chunkSize).coerceAtMost(episodes.size) else episodes.size

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (epIdx in startEpIndex until endEpIndex) {
                                val ep = episodes[epIdx]
                                val isPlayingEp = epIdx == currentEpIdx
                                val resume = onGetEpisodeResume(currentMovie.effectiveKey, epIdx)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isPlayingEp -> Color.White
                                                resume != null -> Color(0xFF272B3A)
                                                else -> AppleGlassSurface
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isPlayingEp) Color.White else AppleGlassBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { switchToEpisode(epIdx) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (resume != null && !isPlayingEp) {
                                            "${ep.name} · ${FormatTime.formatTime(resume.currentTime.toDouble())}"
                                        } else {
                                            ep.name
                                        },
                                        color = if (isPlayingEp) Color.Black else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isPlayingEp) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    sizeDp: Int = 44,
    iconSizeDp: Int = 20,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        color = if (enabled) AppleGlassSurface else Color(0x591C1C1E),
        shape = CircleShape,
        modifier = Modifier
            .size(sizeDp.dp)
            .border(
                width = 1.dp,
                color = if (enabled) AppleGlassBorder else Color(0x1AFFFFFF),
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color(0x59FFFFFF),
                modifier = Modifier.size(iconSizeDp.dp)
            )
        }
    }
}

@Composable
private fun AppleSeekCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String,
    onClick: () -> Unit
) {
    Surface(
        color = AppleGlassSurface,
        shape = CircleShape,
        modifier = Modifier
            .size(56.dp)
            .border(1.dp, AppleGlassBorder, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badgeText,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = badgeText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppleControlPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) Color.White else AppleGlassSurface,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (isActive) Color.White else AppleGlassBorder,
                shape = RoundedCornerShape(999.dp)
            )
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = if (isActive) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SeekRippleBadge(
    isRewind: Boolean,
    seconds: Int
) {
    Column(
        modifier = Modifier
            .background(AppleGlassActive, CircleShape)
            .border(1.dp, AppleGlassBorder, CircleShape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRewind) "⟪⟪" else "⟫⟫",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${if (isRewind) "-" else "+"}${seconds}s",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FallbackEmbedWebView(
    embedUrl: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (_: Exception) {}
            }
            webViewRef = null
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                if (embedUrl.isNotBlank()) {
                    val headers = when {
                        embedUrl.contains("streamc.xyz", ignoreCase = true) ->
                            mapOf("Referer" to "https://phim.nguonc.com/")
                        embedUrl.contains("streamvsmov.com", ignoreCase = true) ->
                            mapOf("Referer" to "https://vsmov.com/")
                        else -> emptyMap()
                    }
                    loadUrl(embedUrl, headers)
                }
                webViewRef = this
            }
        },
        update = { webView ->
            webViewRef = webView
            if (webView.url != embedUrl && embedUrl.isNotBlank()) {
                val headers = when {
                    embedUrl.contains("streamc.xyz", ignoreCase = true) ->
                        mapOf("Referer" to "https://phim.nguonc.com/")
                    embedUrl.contains("streamvsmov.com", ignoreCase = true) ->
                        mapOf("Referer" to "https://vsmov.com/")
                    else -> emptyMap()
                }
                webView.loadUrl(embedUrl, headers)
            }
        },
        modifier = modifier
    )
}

private fun shareEpisodeLink(
    context: Context,
    movie: Movie,
    onShowToast: (String) -> Unit
) {
    val slug = movie.effectiveKey
    val epNumber = (movie.currentEpisodeIndex + 1).coerceAtLeast(1)
    val shareUrl = UrlUtils.buildEpisodeUrl(slug, epNumber)
    val shareText = "Xem phim \"${movie.title}\" trên BACUFlix: $shareUrl"
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, movie.title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ tập phim"))
    } catch (_: Exception) {
        onShowToast("Đã tạo liên kết: $shareUrl")
    }
}
