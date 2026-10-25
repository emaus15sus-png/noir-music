package com.noir.music

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the player so music keeps going when you leave the app; also runs autoplay and taste tracking. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var session: MediaSession
    private val scope = MainScope()
    private var current: Song? = null
    private var startedAt = 0L
    private var extending = false

    override fun onCreate() {
        super.onCreate()
        val source = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { spec ->
            if (spec.uri.scheme == "yt") spec.withUri(Uri.parse(Yt.audioUrl(spec.uri.lastPathSegment!!))) else spec
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            // Start after 0.5 s of audio instead of the default 2.5 s
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 500, 1_000).build())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val now = SystemClock.elapsedRealtime()
                current?.let { Taste.record(it, reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, now - startedAt) }
                current = item?.toSong()
                startedAt = now
                extendIfNeeded(player)
                if (player.hasNextMediaItem()) Yt.prefetch(player.getMediaItemAt(player.nextMediaItemIndex).mediaId)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Unplayable or expired stream: drop the cached link and move on
                player.currentMediaItem?.let { Yt.forget(it.mediaId) }
                skipBroken(player)
            }
        })

        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            // Controllers strip URIs from media items, so rebuild them from the id
            override fun onAddMediaItems(
                mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> =
                Futures.immediateFuture(mediaItems.map { it.buildUpon().setUri(songUri(it.mediaId)).build() }.toMutableList())
        }).build()
    }

    /** Autoplay: keep at least two songs ahead by appending the current song's radio, ranked by taste. */
    private fun extendIfNeeded(player: Player) {
        val seed = player.currentMediaItem?.mediaId ?: return
        if (extending || player.mediaItemCount - player.currentMediaItemIndex > 2) return
        extending = true
        scope.launch {
            val queued = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }.toSet()
            val next = runCatching { withContext(Dispatchers.IO) { Taste.rank(Yt.radio(seed)) } }.getOrDefault(emptyList())
            player.addMediaItems(next.filter { it.id !in queued }.take(10).map { it.toItem() })
            extending = false
            if (player.playerError != null) skipBroken(player)
        }
    }

    private fun skipBroken(player: Player) {
        if (!player.hasNextMediaItem()) return
        player.seekToNextMediaItem()
        player.prepare()
        player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!session.player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.player.release()
        session.release()
        super.onDestroy()
    }
}
