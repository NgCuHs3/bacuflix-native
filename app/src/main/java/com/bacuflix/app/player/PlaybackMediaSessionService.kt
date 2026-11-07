package com.bacuflix.app.player

import android.app.PendingIntent
import android.content.Intent
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
 * lock screen controls, Bluetooth headset buttons, and Next/Previous episode routing.
 */
@OptIn(UnstableApi::class)
class PlaybackMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.mipmap.ic_launcher)
            }
        setMediaNotificationProvider(notificationProvider)

        val basePlayer = PlayerController.getOrCreatePlayer(this)
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

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
