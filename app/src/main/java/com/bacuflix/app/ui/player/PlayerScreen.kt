package com.bacuflix.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bacuflix.app.model.EpisodeResumeInfo
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.SubtitleCue
import com.bacuflix.app.player.PlayerController
import com.bacuflix.app.player.SeekController
import com.bacuflix.app.player.SubtitleParser
import com.bacuflix.app.ui.theme.BacuFlixColors
import com.bacuflix.app.util.FormatTime
import com.bacuflix.app.util.UrlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
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
    val coroutineScope = rememberCoroutineScope()

    var currentMovie by remember(initialMovie.id) { mutableStateOf(initialMovie) }
    val playbackState by PlayerController.uiState.collectAsState()

    val isEmbedOnly = remember(currentMovie.m3u8, currentMovie.fallbackEmbed) {
        val m3u8 = currentMovie.m3u8.trim()
        (!m3u8.contains(".m3u8", ignoreCase = true) || m3u8.isEmpty()) &&
            currentMovie.fallbackEmbed.isNotBlank()
    }
    var forceFallbackEmbed by remember(currentMovie.id) { mutableStateOf(false) }
    val useWebViewEmbed = isEmbedOnly || forceFallbackEmbed

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTick by remember { mutableIntStateOf(0) }
    var isFullscreenLandscape by remember { mutableStateOf(false) }

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

    // Auto-next episode countdown state
    var autoNextRemainingSec by remember { mutableIntStateOf(0) }
    var autoNextCancelled by remember(currentMovie.id) { mutableStateOf(false) }

    val episodes = currentMovie.episodesList
    val currentEpIdx = currentMovie.currentEpisodeIndex
    val hasPrevEpisode = episodes.isNotEmpty() && currentEpIdx > 0
    val hasNextEpisode = episodes.isNotEmpty() && currentEpIdx < episodes.lastIndex

    fun saveCurrentProgress() {
        if (useWebViewEmbed) return
        val curSec = (positionMs / 1000L).toInt()
        val durSec = (durationMs / 1000L).toInt()
        if (curSec > 2) {
            onSaveProgress(currentMovie, curSec, durSec)
        }
    }

    fun switchToEpisode(targetIndex: Int) {
        if (targetIndex !in episodes.indices) return
        saveCurrentProgress()
        val ep = episodes[targetIndex]
        val m3u8 = ep.linkM3u8.trim()
        val embed = ep.linkEmbed.trim()
        val seriesTitle = currentMovie.seriesTitle.ifBlank {
            currentMovie.title.substringBefore(" - ")
        }
        val epName = ep.name.ifBlank { "Tập ${targetIndex + 1}" }
        val savedResume = onGetEpisodeResume(currentMovie.effectiveKey, targetIndex)?.currentTime ?: 0

        forceFallbackEmbed = false
        autoNextRemainingSec = 0
        autoNextCancelled = false
        currentMovie = currentMovie.copy(
            id = "${currentMovie.effectiveKey}-$targetIndex",
            title = "$seriesTitle - $epName",
            seriesTitle = seriesTitle,
            episodeName = epName,
            currentEpisodeIndex = targetIndex,
            m3u8 = m3u8.ifBlank { embed },
            fallbackEmbed = embed,
            resumeTime = savedResume
        )
    }

    fun performRelativeSeek(deltaSeconds: Int) {
        val player = PlayerController.getOrCreatePlayer(context)
        val curSec = player.currentPosition / 1000.0
        val durSec = if (player.duration > 0) player.duration / 1000.0 else Double.NaN
        val targetSec = SeekController.computeRelativeSeekTarget(curSec, durSec, deltaSeconds.toDouble())
        val targetMs = (targetSec * 1000.0).toLong().coerceAtLeast(0L)
        player.seekTo(targetMs)
        positionMs = targetMs
    }

    fun adjustVolume(delta: Float) {
        val player = PlayerController.getOrCreatePlayer(context)
        val nextVol = (playerVolume + delta).coerceIn(0f, 1f)
        playerVolume = nextVol
        player.volume = nextVol
        volumeHudVisible = true
        volumeHudTick++
        lastInteractionTick++
    }

    fun toggleFullscreenOrientation() {
        val act = activity ?: return
        isFullscreenLandscape = !isFullscreenLandscape
        act.requestedOrientation = if (isFullscreenLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val window = act.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreenLandscape) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        saveCurrentProgress()
        if (isFullscreenLandscape) {
            toggleFullscreenOrientation()
        }
        PlayerController.stopAndRelease(context)
        onBackToDetail()
    }

    // Keep screen awake during playback & cleanup on exit
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            saveCurrentProgress()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            PlayerController.onNextEpisodeCallback = null
            PlayerController.onPreviousEpisodeCallback = null
            PlayerController.stopAndRelease(context)
        }
    }

    // Register MediaSession next/prev episode callbacks
    LaunchedEffect(currentMovie.id, currentEpIdx, episodes.size) {
        PlayerController.onNextEpisodeCallback = if (hasNextEpisode) {
            { switchToEpisode(currentEpIdx + 1) }
        } else null
        PlayerController.onPreviousEpisodeCallback = if (hasPrevEpisode) {
            { switchToEpisode(currentEpIdx - 1) }
        } else null
    }

    // Start playback when currentMovie changes
    LaunchedEffect(currentMovie.id, useWebViewEmbed) {
        onRecordPlayInteraction(currentMovie)
        if (!useWebViewEmbed) {
            val startMs = (currentMovie.resumeTime.coerceAtLeast(0)) * 1000L
            PlayerController.startPlayback(context, currentMovie, startMs)
            if (currentMovie.resumeTime > 5) {
                onShowToast("Tiếp tục phát từ ${FormatTime.formatTime(currentMovie.resumeTime.toDouble())}")
            }
        }
        // Load external subtitle if provided
        if (currentMovie.subtitle.isNotBlank()) {
            try {
                subtitleCues = SubtitleParser.loadSubtitleFromUrl(currentMovie.subtitle)
            } catch (_: Exception) {
                subtitleCues = emptyList()
            }
        } else {
            subtitleCues = emptyList()
        }
    }

    // Poll ExoPlayer position & update subtitles every 250ms, save progress every 4s
    LaunchedEffect(useWebViewEmbed, currentMovie.id, subtitlesEnabled, subtitleCues) {
        if (useWebViewEmbed) return@LaunchedEffect
        var tickCount = 0
        while (isActive) {
            val player = PlayerController.getOrCreatePlayer(context)
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            positionMs = pos
            if (dur > 0L) durationMs = dur

            if (subtitlesEnabled && subtitleCues.isNotEmpty()) {
                activeSubtitleText = SubtitleParser.getActiveSubtitleText(subtitleCues, pos / 1000.0)
            } else {
                activeSubtitleText = ""
            }

            tickCount++
            if (tickCount % 16 == 0 && player.isPlaying) {
                saveCurrentProgress()
            }
            delay(250L)
        }
    }

    // Auto-hide controls after 4.5s while playing
    LaunchedEffect(controlsVisible, lastInteractionTick, playbackState.isPlaying) {
        if (controlsVisible && playbackState.isPlaying && autoNextRemainingSec == 0) {
            delay(4500L)
            controlsVisible = false
        }
    }

    // Auto-hide volume HUD after 1.6s
    LaunchedEffect(volumeHudVisible, volumeHudTick) {
        if (volumeHudVisible) {
            delay(1600L)
            volumeHudVisible = false
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

    // Trigger 5-second Auto-Next Episode countdown when playback ends
    LaunchedEffect(playbackState.isEnded, hasNextEpisode, autoNextCancelled) {
        if (playbackState.isEnded && hasNextEpisode && !autoNextCancelled) {
            for (sec in 5 downTo 1) {
                autoNextRemainingSec = sec
                delay(1000L)
            }
            autoNextRemainingSec = 0
            switchToEpisode(currentEpIdx + 1)
        } else {
            autoNextRemainingSec = 0
        }
    }

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
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = PlayerController.getOrCreatePlayer(ctx)
                    }
                },
                update = { view ->
                    val targetPlayer = PlayerController.getOrCreatePlayer(context)
                    if (view.player !== targetPlayer) {
                        view.player = targetPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Left / Center / Right Gesture Zones for Single-Tap Controls & Double-Tap 10s Seek
            Row(modifier = Modifier.fillMaxSize()) {
                // Left 35%: Double-tap to rewind 10s
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTick++
                                },
                                onDoubleTap = {
                                    performRelativeSeek(-10)
                                    leftSeekCumulativeSec += 10
                                    lastInteractionTick++
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedVisibility(
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

                // Center 30%: Single-tap to toggle controls, Double-tap to Play/Pause
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTick++
                                },
                                onDoubleTap = {
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
                            )
                        }
                )

                // Right 35%: Double-tap to forward 10s
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    controlsVisible = !controlsVisible
                                    lastInteractionTick++
                                },
                                onDoubleTap = {
                                    performRelativeSeek(10)
                                    rightSeekCumulativeSec += 10
                                    lastInteractionTick++
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedVisibility(
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

        // External Subtitle Overlay
        if (!useWebViewEmbed && subtitlesEnabled && activeSubtitleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (controlsVisible) 150.dp else 38.dp,
                        start = 24.dp,
                        end = 24.dp
                    )
                    .background(Color(0xBF000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeSubtitleText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Volume HUD Overlay
        AnimatedVisibility(
            visible = volumeHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 24.dp)
        ) {
            val pct = (playerVolume * 100f).roundToInt()
            Row(
                modifier = Modifier
                    .background(Color(0xD9121318), RoundedCornerShape(999.dp))
                    .border(1.dp, BacuFlixColors.BorderSubtle, RoundedCornerShape(999.dp))
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
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(playerVolume.coerceIn(0f, 1f))
                            .background(BacuFlixColors.AccentRed)
                    )
                }
                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
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
                            .background(Color(0xEB121318), RoundedCornerShape(14.dp))
                            .border(1.dp, BacuFlixColors.BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = playbackState.errorMessage ?: "Lỗi phát video",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val resumeMs = positionMs.coerceAtLeast(0L)
                                    PlayerController.startPlayback(context, currentMovie, resumeMs)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BacuFlixColors.AccentRed)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Thử lại", fontWeight = FontWeight.Bold)
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
                            color = BacuFlixColors.AccentRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Đang tải luồng phát...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 5-Second Auto-Next Episode Countdown Overlay
        AnimatedVisibility(
            visible = autoNextRemainingSec > 0 && hasNextEpisode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 140.dp, end = 24.dp)
        ) {
            val nextEp = episodes.getOrNull(currentEpIdx + 1)
            val nextEpTitle = nextEp?.name?.ifBlank { "Tập ${currentEpIdx + 2}" } ?: "Tập tiếp theo"
            Column(
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 340.dp)
                    .background(Color(0xF212141C), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x4DE50914), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "TẬP TIẾP THEO",
                    color = BacuFlixColors.AccentRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${currentMovie.seriesTitle.ifBlank { currentMovie.title }} - $nextEpTitle",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tự động phát sau ${autoNextRemainingSec}s",
                    color = BacuFlixColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { switchToEpisode(currentEpIdx + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = BacuFlixColors.AccentRed),
                        shape = RoundedCornerShape(8.dp)
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Top Bar & Bottom Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible || useWebViewEmbed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xD9000000), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Back to Movie Detail button
                    Surface(
                        color = Color(0x9914161D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, BacuFlixColors.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable {
                                saveCurrentProgress()
                                if (isFullscreenLandscape) toggleFullscreenOrientation()
                                PlayerController.stopAndRelease(context)
                                onBackToDetail()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Back",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // BACUFlix Brand Button (returns straight to Home)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = BacuFlixColors.AccentRed, fontWeight = FontWeight.Black)) {
                                append("BACU")
                            }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append("Flix")
                            }
                        },
                        fontSize = 19.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                saveCurrentProgress()
                                if (isFullscreenLandscape) toggleFullscreenOrientation()
                                PlayerController.stopAndRelease(context)
                                onReturnHome()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    // Now Playing Pill + Movie/Episode Title
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BacuFlixColors.AccentRed)
                        )
                        Text(
                            text = "NOW PLAYING",
                            color = BacuFlixColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = currentMovie.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Controls (only for native ExoPlayer mode)
                if (!useWebViewEmbed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xE6000000))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Timeline Seek Bar + Time Labels
                        val curSec = (positionMs / 1000L).toDouble()
                        val durSec = (durationMs / 1000L).toDouble()
                        val progressFraction = if (isDraggingSlider) {
                            sliderDragFraction
                        } else if (durationMs > 0L) {
                            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = FormatTime.formatTime(
                                    if (isDraggingSlider && durSec > 0) sliderDragFraction * durSec else curSec
                                ),
                                color = Color.White,
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
                                    thumbColor = BacuFlixColors.AccentRed,
                                    activeTrackColor = BacuFlixColors.AccentRed,
                                    inactiveTrackColor = Color(0x4DFFFFFF)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = FormatTime.formatTime(durSec),
                                color = BacuFlixColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Control Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Primary Transport Controls
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasPrevEpisode) {
                                    PlayerActionButton(
                                        icon = Icons.Default.SkipPrevious,
                                        label = "Tập trước",
                                        onClick = { switchToEpisode(currentEpIdx - 1) }
                                    )
                                }

                                PlayerActionButton(
                                    icon = Icons.Default.FastRewind,
                                    label = "-10s",
                                    onClick = {
                                        performRelativeSeek(-10)
                                        lastInteractionTick++
                                    }
                                )

                                // Play / Pause Primary Button
                                Button(
                                    onClick = {
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
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BacuFlixColors.AccentRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (playbackState.isPlaying) "Pause" else "Play",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                PlayerActionButton(
                                    icon = Icons.Default.FastForward,
                                    label = "+10s",
                                    onClick = {
                                        performRelativeSeek(10)
                                        lastInteractionTick++
                                    }
                                )

                                if (hasNextEpisode) {
                                    PlayerActionButton(
                                        icon = Icons.Default.SkipNext,
                                        label = "Tập tiếp",
                                        onClick = { switchToEpisode(currentEpIdx + 1) }
                                    )
                                }

                                PlayerActionButton(
                                    icon = Icons.Default.Share,
                                    label = "Chia sẻ",
                                    onClick = {
                                        shareEpisodeLink(
                                            context = context,
                                            movie = currentMovie,
                                            onShowToast = onShowToast
                                        )
                                        lastInteractionTick++
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Secondary Controls (Volume, Subtitles, Fullscreen)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlayerActionButton(
                                    icon = Icons.AutoMirrored.Filled.VolumeOff,
                                    label = "Vol -",
                                    onClick = { adjustVolume(-0.1f) }
                                )
                                PlayerActionButton(
                                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                                    label = "Vol +",
                                    onClick = { adjustVolume(0.1f) }
                                )
                                PlayerActionButton(
                                    icon = Icons.Default.ClosedCaption,
                                    label = if (subtitlesEnabled) "Subs ON" else "Subs OFF",
                                    isActive = subtitlesEnabled && subtitleCues.isNotEmpty(),
                                    onClick = {
                                        subtitlesEnabled = !subtitlesEnabled
                                        onShowToast(if (subtitlesEnabled) "Đã bật phụ đề" else "Đã tắt phụ đề")
                                        lastInteractionTick++
                                    }
                                )
                                PlayerActionButton(
                                    icon = if (isFullscreenLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    label = if (isFullscreenLandscape) "Exit Full" else "Full screen",
                                    onClick = {
                                        toggleFullscreenOrientation()
                                        lastInteractionTick++
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) Color(0x33E50914) else Color(0x99181A22),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (isActive) BacuFlixColors.AccentRed else BacuFlixColors.BorderSubtle,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = Color.White,
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
            .background(Color(0x99000000), CircleShape)
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
            text = "$seconds seconds",
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
                loadUrl(embedUrl)
            }
        },
        update = { webView ->
            if (webView.url != embedUrl && embedUrl.isNotBlank()) {
                webView.loadUrl(embedUrl)
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
    val shareUrl = UrlUtils.buildShareUrl(slug, epNumber)
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
