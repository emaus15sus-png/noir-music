package com.noir.music

/**
 * A [mood] mix ready to play, or empty if nothing loaded. Counts as a pick for the Mood DJ to learn from.
 * With a [prompt] it is also saved as a playlist named after it; the name is returned.
 */
suspend fun buildMix(mood: Mood, prompt: String? = null, extra: String = ""): Pair<String?, List<Song>> {
    val songs = tvMix(mood, extra)
    if (songs.isEmpty()) return null to songs
    Taste.pickMood(Mood.slot(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)), mood)
    val name = prompt?.let { Taste.freeName("${mood.label} · ${it.trim().take(24)}") }
    name?.let { Taste.setPlaylist(it, songs) }
    return name to songs
}

/** [moodMix] with this TV's taste: its favourite artists, and its ranking (nothing heard recently or skipped twice). */
suspend fun tvMix(mood: Mood, extra: String = "") = moodMix(mood, extra, Taste.topArtists(3)) { Taste.rank(it, perArtist = 4) }
