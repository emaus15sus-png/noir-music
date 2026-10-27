package com.noir.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * On-device "Mood DJ". Reads a mood from what you type or dictate (English and Hinglish keywords),
 * or guesses it from the time of day plus the moods you picked before at that time, then builds a mix
 * from your favourite artists and the mood. No cloud AI: it runs offline, so it is free and private.
 */
enum class Mood(val label: String, val words: String, val from: Long, val to: Long, private val lexicon: List<String>) {
    Happy("Happy", "happy upbeat", 0xFFFFB347, 0xFFFF5E62, listOf("happy", "good", "great", "excit", "joy", "awesome", "amazing", "celebrat", "fun", "khush", "mast", "badhiya")),
    Chill("Chill", "chill relaxing", 0xFF43C6AC, 0xFF191654, listOf("chill", "relax", "calm", "tired", "lazy", "peace", "cozy", "rain", "slow", "sleep", "sukoon", "aaram")),
    Energetic("Energetic", "workout energetic", 0xFFF7971E, 0xFFFF2E63, listOf("energ", "workout", "gym", "pump", "running", "power", "hype", "motivat", "josh")),
    Romantic("Romantic", "romantic love", 0xFFFF6A88, 0xFF8E2DE2, listOf("love", "romantic", "romance", "crush", "date", "pyaar", "pyar", "ishq", "mohabbat")),
    Sad("Sad", "sad emotional", 0xFF4B6CB7, 0xFF182848, listOf("sad", "down", "lonely", "alone", "cry", "hurt", "broken", "heartbreak", "upset", "depress", "miss", "udaas", "udas", "dukh", "dard")),
    Focus("Focus", "focus instrumental", 0xFF00C9FF, 0xFF1F4037, listOf("focus", "study", "studying", "work", "concentrat", "coding", "reading", "padhai")),
    Party("Party", "party dance", 0xFFFC466B, 0xFF3F5EFB, listOf("party", "dance", "club", "weekend", "friends", "naach", "nacho"));

    private fun hit(token: String) = lexicon.any { token.startsWith(it) }

    companion object {
        private val negations = setOf("not", "no", "never", "dont", "don't", "isnt", "nahi", "nahin", "na")
        private val hindiAfter = setOf("nahi", "nahin", "na")
        private val filler = setOf(
            "i", "im", "i'm", "am", "feel", "feeling", "feels", "like", "want", "wanna", "to", "some", "song", "songs", "music",
            "play", "me", "my", "a", "the", "so", "very", "really", "today", "mood", "is", "and", "for", "with", "of", "in", "it",
            "kinda", "bit", "little", "something", "kuch", "hai", "hoon", "hu", "mujhe", "mera", "sunao", "gaane", "gane", "gana",
        ) + negations

        /** Strongest mood in [text] (null if none) and the words that were not about mood, e.g. "90s bollywood". */
        fun detect(text: String): Pair<Mood?, String> {
            val tokens = text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotBlank() }
            val score = IntArray(entries.size)
            val leftover = mutableListOf<String>()
            tokens.forEachIndexed { i, t ->
                val m = entries.firstOrNull { it.hit(t) }
                if (m == null) { if (t !in filler) leftover += t; return@forEachIndexed }
                // English negates before the word ("not happy"), Hindi after it ("khush nahi")
                val negated = tokens.subList(maxOf(0, i - 2), i).any { it in negations } ||
                    tokens.subList(i + 1, minOf(tokens.size, i + 3)).any { it in hindiAfter }
                // "not happy" means sad; "not sad" says nothing useful
                when {
                    !negated -> score[m.ordinal] += 2
                    m == Happy || m == Energetic || m == Party -> score[Sad.ordinal] += 1
                }
            }
            val best = score.indices.maxBy { score[it] }
            return (if (score[best] > 0) entries[best] else null) to leftover.joinToString(" ")
        }

        fun slot(hour: Int) = when (hour) { in 5..11 -> 0; in 12..16 -> 1; in 17..20 -> 2; else -> 3 }
        val slotNames = listOf("morning", "afternoon", "evening", "night")

        // How well each mood fits each time slot (morning, afternoon, evening, night)
        private val fit = mapOf(
            Happy to intArrayOf(3, 1, 2, 0), Chill to intArrayOf(0, 1, 3, 3), Energetic to intArrayOf(2, 2, 0, 0),
            Romantic to intArrayOf(0, 0, 1, 2), Sad to intArrayOf(0, 0, 0, 1), Focus to intArrayOf(2, 3, 0, 0), Party to intArrayOf(0, 0, 1, 0),
        )

        /** Best guess for right now: time-of-day fit plus how often you picked each mood in this slot before. */
        fun auto(hour: Int, picks: (slot: Int, Mood) -> Int): Mood {
            val s = slot(hour)
            return entries.maxBy { fit.getValue(it)[s] + picks(s, it) }
        }

        /** Round-robin merge so one artist doesn't fill the start of the mix. */
        fun <T> interleave(lists: List<List<T>>): List<T> =
            (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }
}

/**
 * A [mood] mix ready to play, or empty if nothing loaded. Counts as a pick for the Mood DJ to learn from.
 * With a [prompt] it is also saved as a playlist named after it; the name is returned.
 */
suspend fun buildMix(mood: Mood, prompt: String? = null, extra: String = ""): Pair<String?, List<Song>> {
    val songs = moodMix(mood, extra)
    if (songs.isEmpty()) return null to songs
    Taste.pickMood(Mood.slot(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)), mood)
    val name = prompt?.let { Taste.freeName("${mood.label} · ${it.trim().take(24)}") }
    name?.let { Taste.setPlaylist(it, songs) }
    return name to songs
}

/** Builds a mix for [mood]: your top artists in that mood, the mood in general, and any extra words you said. */
suspend fun moodMix(mood: Mood, extra: String = ""): List<Song> = coroutineScope {
    val queries = buildList {
        if (extra.isNotBlank()) add("$extra ${mood.words} songs")
        Taste.topArtists(3).forEach { add("$it ${mood.words} songs") }
        add("${mood.words} songs")
    }
    fun search(q: String) = runCatching { Yt.search(q) }.getOrDefault(emptyList())
    // The first YouTube Music search of a session sets up its client; parallel cold searches race on that and fail
    val first = withContext(Dispatchers.IO) { search(queries.first()) }
    val found = listOf(first) + queries.drop(1).map { q -> async(Dispatchers.IO) { search(q) } }.awaitAll()
    Taste.rank(Mood.interleave(found), perArtist = 4).take(25)
}
