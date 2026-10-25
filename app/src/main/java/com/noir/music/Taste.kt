package com.noir.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * On-device recommendation profile. Finishing or liking a song raises its artist's score,
 * skipping it in the first 30s lowers it; recommendations are radio candidates ranked by that score.
 */
object Taste {
    private lateinit var p: SharedPreferences

    fun init(c: Context) {
        p = c.getSharedPreferences("taste", Context.MODE_PRIVATE)
    }

    /** Bumped on every list write; reads touch it, so Compose screens showing these lists refresh by themselves. */
    private var version by mutableIntStateOf(0)

    val liked get() = list("liked")
    val history get() = list("history")

    /** Playlist name -> songs, in creation order. */
    val playlists: Map<String, List<Song>>
        get() = JSONObject(read("playlists", "{}")).let { o -> o.keys().asSequence().associateWith { o.getJSONArray(it).songs() } }

    /** Creates the playlist if needed and appends [s] unless it is already there. */
    fun addToPlaylist(name: String, s: Song?) {
        val l = playlists[name].orEmpty()
        setPlaylist(name, if (s == null || l.any { it.id == s.id }) l else l + s)
    }

    /** null deletes the playlist. */
    fun setPlaylist(name: String, songs: List<Song>?) {
        val o = JSONObject(read("playlists", "{}"))
        if (songs == null) o.remove(name) else o.put(name, songs.arr())
        write("playlists", o.toString())
    }

    fun isLiked(id: String) = liked.any { it.id == id }

    fun toggleLike(s: Song): Boolean {
        val l = liked
        val now = l.none { it.id == s.id }
        save("liked", if (now) listOf(s) + l else l.filter { it.id != s.id })
        bump(s.artist, if (now) 3.0 else -3.0)
        return now
    }

    fun record(s: Song, completed: Boolean, listenedMs: Long) {
        val skipped = !completed && listenedMs < 30_000
        bump(s.artist, if (completed) 1.0 else if (skipped) -1.0 else 0.3)
        if (skipped) p.edit().putInt("skip_${s.id}", skips(s.id) + 1).apply()
        else save("history", (listOf(s) + history.filter { it.id != s.id }).take(100))
    }

    /** Artists you like most (lowercase names), best first. */
    fun topArtists(n: Int): List<String> = scores().let { s ->
        s.keys().asSequence().filter { s.getDouble(it) > 0 }.sortedByDescending { s.getDouble(it) }.take(n).toList()
    }

    /** How often you started a [mood] mix in this time [slot]; the Mood DJ learns from it. */
    fun moodPicks(slot: Int, mood: Mood) = p.getInt("mood_${slot}_${mood.name}", 0)
    fun pickMood(slot: Int, mood: Mood) = p.edit().putInt("mood_${slot}_${mood.name}", moodPicks(slot, mood) + 1).apply()

    /** [name], or "name 2", "name 3"… if a playlist already uses it, so a new mix never overwrites one. */
    fun freeName(name: String) = generateSequence(2) { it + 1 }.map { "$name $it" }.let { more ->
        (sequenceOf(name) + more).first { it !in playlists }
    }

    /** Favourite artists first, random jitter for discovery, max [perArtist] per artist, nothing heard recently or skipped twice. */
    fun rank(candidates: List<Song>, perArtist: Int = 2): List<Song> {
        val scores = scores()
        val recent = history.take(40).map { it.id }.toSet()
        val count = HashMap<String, Int>()
        return candidates.distinctBy { it.id }
            .filter { it.id !in recent && skips(it.id) < 2 }
            .sortedByDescending { scores.optDouble(key(it.artist), 0.0) + Random.nextDouble(0.0, 2.0) }
            .filter { val n = (count[key(it.artist)] ?: 0) + 1; count[key(it.artist)] = n; n <= perArtist }
    }

    /** Songs to build "Made for you" from: a couple of liked songs plus recent listens. */
    fun seeds(): List<String> =
        (liked.shuffled().take(2) + history.take(3).shuffled().take(1) + history.drop(3).take(20).shuffled().take(1))
            .distinctBy { it.id }.map { it.id }

    private fun key(artist: String) =
        artist.lowercase().split(",", "&", " feat", " ft.", " x ").first().trim()

    private fun skips(id: String) = p.getInt("skip_$id", 0)
    private fun scores() = JSONObject(p.getString("scores", "{}")!!)

    private fun bump(artist: String, d: Double) {
        val s = scores()
        p.edit().putString("scores", s.put(key(artist), s.optDouble(key(artist), 0.0) + d).toString()).apply()
    }

    private fun read(k: String, def: String): String { version; return p.getString(k, def)!! }
    private fun write(k: String, v: String) { p.edit().putString(k, v).apply(); version++ }
    private fun JSONArray.songs() = List(length()) { getJSONObject(it).song() }
    private fun List<Song>.arr() = JSONArray(map { it.json() })
    private fun list(k: String) = JSONArray(read(k, "[]")).songs()
    private fun save(k: String, l: List<Song>) = write(k, l.arr().toString())
}
