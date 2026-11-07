package com.bacuflix.app.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bacuflix.app.model.Movie
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared singleton controller managing the native AndroidX Media3 [ExoPlayer] instance,
 * custom [PngStrippingDataSource.Factory] for PNG-wrapped HLS streams, and
 * [PlaybackMediaSessionService] foreground notification & background playback integration.
 */
@OptIn(UnstableApi::class)
object PlayerController {
    data class PlaybackUiState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = true,
        val isEnded: Boolean = false,
        val errorMessage: String? = null,
        val currentMovie: Movie? = null,
        val currentEpisodeIndex: Int = 0
    )

    private var exoPlayer: ExoPlayer? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val dataSourceFactory = PngStrippingDataSource.Factory()
    private var isInBackground: Boolean = false

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    var onNextEpisodeCallback: (() -> Unit)? = null
    var onPreviousEpisodeCallback: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.value = _uiState.value.copy(
                isPlaying = isPlaying,
                errorMessage = if (isPlaying) null else _uiState.value.errorMessage
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val ended = playbackState == Player.STATE_ENDED
            _uiState.value = _uiState.value.copy(
                isBuffering = buffering,
                isEnded = ended
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = exoPlayer
            // If a surface/decoder error occurs while transitioning to/from background,
            // clear the video surface and automatically recover at the current position.
            if (
                player != null &&
                (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_SURFACE_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
            ) {
                try {
                    val resumePos = player.currentPosition.coerceAtLeast(0L)
                    player.clearVideoSurface()
                    player.seekTo(resumePos)
                    player.prepare()
                    player.playWhenReady = true
                    return
                } catch (_: Exception) {}
            }

            val detail = error.localizedMessage ?: "Lỗi giải mã luồng phát (${error.errorCodeName})"
            _uiState.value = _uiState.value.copy(
                isBuffering = false,
                isPlaying = false,
                errorMessage = "Không thể phát luồng video: $detail"
            )
        }
    }

    fun peekPlayer(): ExoPlayer? = exoPlayer

    @Synchronized
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        exoPlayer?.let { return it }
        val appContext = context.applicationContext
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 4_000
            )
            .setBackBuffer(15_000, true)
            .build()

        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(playerListener)
        exoPlayer = player
        ensureMediaControllerConnected(appContext)
        return player
    }

    fun setBackgroundMode(inBackground: Boolean) {
        isInBackground = inBackground
        val player = exoPlayer ?: return
        if (inBackground) {
            try {
                player.clearVideoSurface()
            } catch (_: Exception) {}
        }
    }

    fun startPlayback(context: Context, movie: Movie, startPositionMs: Long = 0L) {
        val appContext = context.applicationContext
        val player = getOrCreatePlayer(appContext)
        val streamUrl = movie.m3u8.trim()

        _uiState.value = PlaybackUiState(
            isPlaying = false,
            isBuffering = true,
            isEnded = false,
            errorMessage = null,
            currentMovie = movie,
            currentEpisodeIndex = movie.currentEpisodeIndex
        )

        if (streamUrl.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isBuffering = false,
                errorMessage = "Không có đường dẫn phát hợp lệ."
            )
            return
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(movie.title)
            .setArtist(movie.source.ifBlank { "BACUFlix" })
            .setAlbumTitle(movie.seriesTitle.ifBlank { movie.title })

        val artworkUrl = movie.thumb.ifBlank { movie.poster }
        if (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://")) {
            metadataBuilder.setArtworkUri(Uri.parse(artworkUrl))
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(movie.id)
            .setUri(streamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
        if (isHls) {
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource, startPositionMs.coerceAtLeast(0L))
        } else {
            player.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
        }

        player.prepare()
        player.playWhenReady = true
        ensureMediaControllerConnected(appContext)
    }

    fun stopAndRelease(context: Context) {
        val appContext = context.applicationContext
        mediaControllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
            } catch (_: Exception) {}
        }
        mediaControllerFuture = null

        try {
            val serviceIntent = Intent(appContext, PlaybackMediaSessionService::class.java)
            appContext.stopService(serviceIntent)
        } catch (_: Exception) {}

        exoPlayer?.let { player ->
            try {
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                player.release()
            } catch (_: Exception) {}
        }
        exoPlayer = null
        isInBackground = false
        _uiState.value = PlaybackUiState()
    }

    private fun ensureMediaControllerConnected(appContext: Context) {
        val existing = mediaControllerFuture
        if (existing != null && !existing.isCancelled) {
            return
        }
        try {
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, PlaybackMediaSessionService::class.java)
            )
            mediaControllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        } catch (_: Exception) {}
    }
}
