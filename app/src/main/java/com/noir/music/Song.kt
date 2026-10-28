package com.noir.music

import org.json.JSONObject

// Plain Kotlin, shared with the PC engine (desktop/engine); Android-only helpers live in Media.kt
data class Song(val id: String, val title: String, val artist: String, val thumb: String, val duration: Long = 0)

fun Song.json(): JSONObject =
    JSONObject().put("id", id).put("t", title).put("a", artist).put("i", thumb).put("d", duration)

fun JSONObject.song() = Song(getString("id"), getString("t"), getString("a"), getString("i"), optLong("d"))
