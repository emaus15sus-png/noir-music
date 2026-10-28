package com.noir.music.engine

import com.noir.music.Mood
import com.noir.music.Song
import com.noir.music.Yt
import com.noir.music.json
import com.noir.music.moodMix
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Noir for PC's engine: the TV app's YouTube Music and Mood DJ code (Yt, Mood) behind a small HTTP API on
 * 127.0.0.1. The desktop app starts it, reads "PORT <n>" from stdout, and sends NOIR_TOKEN as X-Token on every
 * request, so other programs and web pages on this PC can't use it.
 */
fun main() {
    val token = System.getenv("NOIR_TOKEN")?.takeIf { it.length >= 16 } ?: error("NOIR_TOKEN missing")
    Yt.init()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.executor = Executors.newFixedThreadPool(8)
    server.createContext("/") { ex -> try { handle(ex, token) } finally { ex.close() } }
    server.start()
    println("PORT ${server.address.port}")
    System.out.flush()
    // The app holds our stdin open; when it closes (app quit or crashed), stop instead of lingering
    thread { while (System.`in`.read() >= 0) Unit; exitProcess(0) }
}

private fun handle(ex: HttpExchange, token: String) {
    val (code, body) = when {
        ex.requestHeaders.getFirst("X-Token") != token -> 403 to "forbidden"
        else -> runCatching { 200 to route(ex.requestURI.path, query(ex.requestURI.rawQuery)).toString() }
            .getOrElse { (if (it is IllegalArgumentException) 400 else 500) to (it.message ?: it.toString()) }
    }
    val bytes = body.toByteArray()
    ex.responseHeaders.add("Content-Type", if (code == 200) "application/json; charset=utf-8" else "text/plain; charset=utf-8")
    ex.sendResponseHeaders(code, bytes.size.toLong())
    ex.responseBody.use { it.write(bytes) }
}

private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter { '=' in it }.associate {
    val (k, v) = it.split('=', limit = 2)
    URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
}

private fun route(path: String, q: Map<String, String>): Any = when (path) {
    "/search" -> songs(Yt.search(q["q"].orEmpty().take(100)))
    "/radio" -> songs(Yt.radio(id(q)))
    "/stream" -> id(q).let { id -> if (q["fresh"] == "1") Yt.forget(id); JSONObject().put("url", Yt.audioUrl(id)) }
    "/prefetch" -> JSONObject().also { Yt.prefetch(id(q)) }
    "/moods" -> JSONArray(Mood.entries.map {
        JSONObject().put("name", it.name).put("label", it.label).put("from", hex(it.from)).put("to", hex(it.to))
    })
    "/mix" -> mix(q)
    else -> throw IllegalArgumentException("no such endpoint")
}

private val videoId = Regex("[A-Za-z0-9_-]{11}")
private fun id(q: Map<String, String>) = q["id"]?.takeIf { videoId.matches(it) } ?: throw IllegalArgumentException("bad id")
private fun songs(l: List<Song>) = JSONArray(l.map { it.json() })
private fun hex(argb: Long) = "#%06X".format(argb and 0xFFFFFF)

/**
 * A Mood DJ mix. The mood is [q] "mood" if given, else read from "text", else guessed from "hour" and the
 * PC's past picks ("slot:Mood:count,..."). "artists" are the listener's favourites; "exclude" ids heard recently.
 */
private fun mix(q: Map<String, String>): JSONObject {
    val (said, extra) = Mood.detect(q["text"].orEmpty().take(200))
    val hour = q["hour"]?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val picks = q["picks"].orEmpty().split(',').mapNotNull { it.split(':').takeIf { p -> p.size == 3 } }
        .associate { (s, m, n) -> "$s:$m" to (n.toIntOrNull() ?: 0) }
    val mood = Mood.entries.firstOrNull { it.name == q["mood"] } ?: said ?: Mood.auto(hour) { s, m -> picks["$s:${m.name}"] ?: 0 }
    val artists = q["artists"].orEmpty().split('|').filter { it.isNotBlank() }.take(3)
    val exclude = q["exclude"].orEmpty().split(',').toSet()
    val songs = runBlocking { moodMix(mood, extra, artists) { rank(it, exclude) } }
    val slot = Mood.slot(hour)
    return JSONObject().put("mood", mood.name).put("label", mood.label).put("slot", slot).put("slotName", Mood.slotNames[slot])
        .put("songs", songs(songs))
}

/** Fresh songs only, at most 4 per artist, in the order the mix built them. */
private fun rank(list: List<Song>, exclude: Set<String>): List<Song> {
    val perArtist = HashMap<String, Int>()
    return list.distinctBy { it.id }.filter { it.id !in exclude }
        .filter { val n = (perArtist[it.artist.lowercase()] ?: 0) + 1; perArtist[it.artist.lowercase()] = n; n <= 4 }
}
