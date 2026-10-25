package com.noir.music

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONObject

data class Song(val id: String, val title: String, val artist: String, val thumb: String, val duration: Long = 0)

// The queue only carries yt://song/<id>; PlaybackService resolves the real stream URL when it is needed
fun songUri(id: String): Uri = Uri.parse("yt://song/$id")

fun Song.toItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(songUri(id))
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(Uri.parse(thumb)).build()
    )
    .build()

fun MediaItem.toSong() =
    Song(mediaId, mediaMetadata.title.toString(), mediaMetadata.artist.toString(), mediaMetadata.artworkUri.toString())

fun Song.json(): JSONObject =
    JSONObject().put("id", id).put("t", title).put("a", artist).put("i", thumb).put("d", duration)

fun JSONObject.song() = Song(getString("id"), getString("t"), getString("a"), getString("i"), optLong("d"))
