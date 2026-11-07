package com.bacuflix.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bacuflix.app.MainActivity
import com.bacuflix.app.R

/**
 * AndroidX Media3 [MediaSessionService] providing the native MediaStyle notification,
 * lock screen controls, Bluetooth headset buttons, Next/Previous episode routing,
 * and CPU/Wi-Fi wake locks for uninterrupted background playback.
 */
@OptIn(UnstableApi::class)
class PlaybackMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateLocks(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = mediaSession?.player
            val active = player != null &&
                player.playWhenReady &&
                (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)
            updateLocks(active)
        }
    }

    override fun onCreate() {
        super.onCreate()

        initLocks()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.mipmap.ic_launcher)
            }
        setMediaNotificationProvider(notificationProvider)

        val basePlayer = PlayerController.getOrCreatePlayer(this)
        basePlayer.addListener(playerListener)

        val forwardingPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (
                    command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                ) {
                    return true
                }
                return super.isCommandAvailable(command)
            }

            override fun seekToNext() {
                PlayerController.onNextEpisodeCallback?.invoke() ?: super.seekToNext()
            }

            override fun seekToNextMediaItem() {
                PlayerController.onNextEpisodeCallback?.invoke() ?: super.seekToNextMediaItem()
            }

            override fun seekToPrevious() {
                PlayerController.onPreviousEpisodeCallback?.invoke() ?: super.seekToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                PlayerController.onPreviousEpisodeCallback?.invoke() ?: super.seekToPreviousMediaItem()
            }
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            pendingIntentFlags
        )

        val session = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        mediaSession = session
        // Register session immediately with MediaSessionService so MediaNotificationManager
        // promotes the service to foreground with a notification without timing out.
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (
            player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        releaseLocks()
        try {
            PlayerController.peekPlayer()?.removeListener(playerListener)
        } catch (_: Exception) {}
        mediaSession?.run {
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun initLocks() {
        try {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BACUFlix:MediaPlaybackWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {}

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "BACUFlix:MediaPlaybackWifiLock"
            )?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {}
    }

    private fun updateLocks(active: Boolean) {
        try {
            if (active) {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(4 * 60 * 60 * 1000L) // up to 4 hours per play session
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                if (wifiLock?.isHeld == true) {
                    wifiLock?.release()
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }
}
