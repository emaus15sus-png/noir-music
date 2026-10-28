package com.noir.music

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

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
