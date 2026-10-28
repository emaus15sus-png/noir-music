package com.noir.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Spotify Connect-style remote. A small HTTP server on the TV's Wi-Fi address, port [PORT], serves the
 * Noir Remote page (assets/remote.html) and the JSON API it uses. Anyone on your Wi-Fi can open it.
 * Commands are POSTs that must carry an "X-Noir" header: browsers don't let other websites send that,
 * so a page you visit elsewhere can't control your music.
 */
class Remote(private val ctx: Context, private val player: Player) {
    private class Reply(val code: Int, val type: String, val body: ByteArray)

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private val found = ConcurrentHashMap<String, Song>() // songs the remote searched, so /play needs only an id
    @Volatile private var server: ServerSocket? = null

    fun start() = thread(isDaemon = true, name = "noir-remote") {
        // reuseAddress: the Restart button relaunches us while the old socket is still closing
        val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) } }.getOrNull()
            ?: return@thread // port taken: run without a remote
        server = s
        while (!s.isClosed) runCatching { val c = s.accept(); pool.execute { handle(c) } }
    }

    fun stop() {
        runCatching { server?.close() }
        pool.shutdownNow()
    }

    private fun handle(c: Socket) = runCatching {
        c.use {
            it.soTimeout = 5_000
            // Read at most 8 KB of request head; there is never a body (parameters are in the query)
            val buf = ByteArray(8192)
            var n = 0
            val input = it.getInputStream()
            while (n < buf.size && !String(buf, 0, n).contains("\r\n\r\n")) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            val head = String(buf, 0, n)
            if (!head.contains("\r\n\r\n")) return@use write(it, text(431, "request too large"))
            val lines = head.substringBefore("\r\n\r\n").split("\r\n")
            val (method, target) = lines[0].split(" ").takeIf { p -> p.size == 3 } ?: return@use write(it, text(400, "bad request"))
            val reply = refusal(lines)?.let { why -> text(403, why) } ?: route(method, Uri.parse("http://tv$target"))
            write(it, reply)
        }
    }

    private fun route(method: String, u: Uri): Reply = when ("$method ${u.path}") {
        "GET /" -> Reply(200, "text/html; charset=utf-8", ctx.assets.open("remote.html").use { it.readBytes() })
        "GET /logo.png" -> Reply(200, "image/png", ctx.resources.openRawResource(R.drawable.ic_launcher).use { it.readBytes() })
        "GET /hello" -> json(JSONObject().put("app", "noir"))
        "GET /state" -> json(onMain { state() })
        "GET /search" -> {
            val songs = runCatching { Yt.search(u.q("q").take(100)) }.getOrDefault(emptyList())
            songs.forEach { found[it.id] = it }
            songs.take(3).forEach { Yt.prefetch(it.id) } // the PC can't hover-prefetch, so warm up the likely picks
            json(JSONArray(songs.map { it.json() }))
        }
        "GET /playlists" -> json(JSONArray(Taste.playlists.map { (name, l) ->
            JSONObject().put("name", name).put("count", l.size).put("i", l.firstOrNull()?.thumb.orEmpty())
        }))
        "POST /toggle" -> ok { if (player.isPlaying) player.pause() else player.play() }
        "POST /next" -> ok { player.seekToNext() }
        "POST /prev" -> ok { player.seekToPrevious() }
        "POST /seek" -> ok { u.q("ms").toLongOrNull()?.let { player.seekTo(it.coerceAtLeast(0)) } }
        "POST /like" -> ok { player.currentMediaItem?.toSong()?.let { Taste.toggleLike(it) } }
        // Queue positions must still point after the current song (they can shift between a poll and a click)
        "POST /jump" -> ok { u.at()?.let { player.seekTo(it, 0) } }
        "POST /remove" -> ok { u.at()?.let { player.removeMediaItem(it) } }
        // A song the remote searched here, or one the PC app hands over with its details (Spotify Connect-style)
        "POST /play" -> (found[u.q("id")] ?: u.song())?.let { s ->
            ok { start(listOf(s)); u.q("pos").toLongOrNull()?.let { player.seekTo(it.coerceAtLeast(0)) } }
        } ?: text(400, "bad song")
        "POST /add" -> u.song()?.let { s -> ok { player.addMediaItem(s.toItem()) } } ?: text(400, "bad song")
        "POST /playlist" -> Taste.playlists[u.q("name")]?.takeIf { it.isNotEmpty() }?.let { l -> ok { start(l) } } ?: text(404, "no such playlist")
        "POST /mood" -> mood(u.q("text").trim().take(200), save = u.q("save") != "0")
        else -> text(404, "not found")
    }

    private fun state() = JSONObject().apply {
        val i = player.currentMediaItemIndex
        player.currentMediaItem?.let { put("song", it.toSong().json()).put("liked", Taste.isLiked(it.mediaId)) }
        put("playing", player.isPlaying).put("pos", player.currentPosition).put("dur", player.duration.coerceAtLeast(0))
        put("queue", JSONArray((i + 1 until minOf(player.mediaItemCount, i + 31)).map { k -> player.getMediaItemAt(k).toSong().json().put("at", k) }))
    }

    /** Same Mood DJ as the TV: plays a mix for [text]; with [save] it is also kept as a playlist, like "How are you feeling?". */
    private fun mood(text: String, save: Boolean): Reply {
        if (text.isEmpty()) return text(400, "say how you feel")
        val (mood, extra) = Mood.detect(text)
        val fallback = Mood.auto(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Taste::moodPicks)
        val (name, songs) = runBlocking { buildMix(mood ?: fallback, text.takeIf { save }, extra) }
        if (songs.isEmpty()) return text(502, "couldn't load songs")
        onMain { start(songs) }
        return json(JSONObject().put("name", name))
    }

    private fun start(songs: List<Song>) {
        player.setMediaItems(songs.map { it.toItem() }, 0, 0)
        player.prepare()
        player.play()
    }

    private fun Uri.q(key: String) = getQueryParameter(key).orEmpty()
    private fun Uri.song() = remoteSong(q("id"), q("t"), q("a"), q("i"), q("d"))
    private fun Uri.at() = q("i").toIntOrNull()?.takeIf { it > player.currentMediaItemIndex && it < player.mediaItemCount }

    /** ExoPlayer may only be touched on the main thread. */
    private fun <T> onMain(f: () -> T): T = FutureTask(f).also { main.post(it) }.get(5, TimeUnit.SECONDS)
    private fun ok(f: () -> Unit) = onMain(f).let { json(JSONObject().put("ok", true)) }
    private fun json(o: Any) = Reply(200, "application/json; charset=utf-8", o.toString().toByteArray())
    private fun text(code: Int, msg: String) = Reply(code, "text/plain; charset=utf-8", msg.toByteArray())

    private fun write(c: Socket, r: Reply) = c.getOutputStream().run {
        write("HTTP/1.1 ${r.code} ${if (r.code == 200) "OK" else "Error"}\r\nContent-Type: ${r.type}\r\nContent-Length: ${r.body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        write(r.body)
        flush()
    }

    companion object {
        const val PORT = 8686
    }
}

private val videoId = Regex("[A-Za-z0-9_-]{11}")
private val coverHost = Regex("""^https://([a-z0-9-]+\.)*(googleusercontent\.com|ytimg\.com)/""")

/**
 * A song sent by another device on the Wi-Fi, or null if anything looks wrong. The cover must come from
 * YouTube's image servers, so a device can't make the TV fetch arbitrary addresses.
 */
internal fun remoteSong(id: String, title: String, artist: String, thumb: String, duration: String): Song? {
    if (!videoId.matches(id) || title.isBlank() || title.length > 200 || artist.length > 200) return null
    if (thumb.isNotEmpty() && (thumb.length > 1000 || !coverHost.containsMatchIn(thumb))) return null
    return Song(id, title, artist, thumb, duration.toLongOrNull()?.coerceIn(0, 86_400) ?: 0)
}

private val ipHost = Regex("""^(\d{1,3}(\.\d{1,3}){3}|localhost|\[[0-9a-fA-F:.]+])(:\d+)?$""")

/**
 * Why a request (its request line and headers) is refused, or null if it may proceed.
 * - Host must be an IP or localhost. A website using "DNS rebinding" points its own domain at the TV,
 *   so its requests arrive with that domain as Host; the real remote always uses the TV's IP.
 * - Commands (POST) need X-Noir, which a browser won't send from another website's page.
 */
internal fun refusal(lines: List<String>): String? {
    fun header(name: String) = lines.drop(1).firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
    if (!ipHost.matches(header("host").orEmpty())) return "open the remote by the TV's IP address"
    if (lines[0].startsWith("POST ") && header("x-noir") == null) return "missing X-Noir header"
    return null
}
