package com.noir.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** YouTube Music catalogue via NewPipeExtractor: no API key, no ads. All calls block, so run them off the main thread. */
object Yt {
    private val urls = ConcurrentHashMap<String, Pair<String, Long>>()

    // Remixes, slowed/reverb edits, fan re-uploads and similar. Allowed only if the user typed that word in the search.
    private val junk = Regex(
        """\b(remix|rmx|mix|synthwave|phonk|bootleg|slowed|reverb|sped ?up|nightcore|8d|lo-?fi|bass ?boost(ed)?|mashup|cover|karaoke|lyrics?|status|ringtone|shorts|1 hour|10 hours)\b""",
        RegexOption.IGNORE_CASE
    )

    fun init() {
        val l = Locale.getDefault()
        NewPipe.init(OkDownloader, Localization.fromLocale(l), ContentCountry(l.country.ifEmpty { "US" }))
    }

    private val noise = Regex("""\s*[(\[][^)\]]*\b(official|video|visualizer|audio|lyric|hd|4k)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    fun clean(s: Song, query: String = "") =
        (s.duration <= 0 || s.duration in 60..900) && junk.findAll(s.title).all { query.contains(it.value, true) }

    /** Video-style "Artist - Song (Official Video)" becomes "Song". Run after [clean], which needs the raw title. */
    fun tidy(s: Song): Song {
        var t = s.title.replace(noise, "").trim()
        val artist = s.artist.lowercase().split(" and ", ",", "&").first().trim()
        if (" - " in t && artist.isNotEmpty() && artist in t.substringBefore(" - ").lowercase()) t = t.substringAfter(" - ").trim()
        return s.copy(title = t.ifEmpty { s.title })
    }

    /** YouTube Music "Songs" search: official tracks only, so user uploads are already excluded. */
    fun search(q: String): List<Song> {
        val ex = YouTube.getSearchExtractor(q, listOf(MUSIC_SONGS), "")
        ex.fetchPage()
        return ex.initialPage.items.songs().filter { clean(it, q) }.map(::tidy)
    }

    /** Same radio YouTube Music builds for a song (RDAMVM mix); falls back to plain related videos. */
    fun radio(id: String): List<Song> = runCatching {
        PlaylistInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$id&list=RDAMVM$id").relatedItems.songs()
    }.getOrElse {
        StreamInfo.getInfo(YouTube, watch(id)).relatedItems.songs()
    }.filter { it.id != id && clean(it) }.map(::tidy)

    /** One lookup per song at a time: playback waits for a prefetch already in flight instead of starting its own. */
    fun audioUrl(id: String): String {
        synchronized(locks.getOrPut(id) { Any() }) {
            urls[id]?.let { (u, at) -> if (System.currentTimeMillis() - at < 3 * 3_600_000) return u } // YT links expire after ~6h
            val best = StreamInfo.getInfo(YouTube, watch(id)).audioStreams
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxBy { it.averageBitrate }
            urls[id] = best.content to System.currentTimeMillis()
            return best.content
        }
    }

    /** Resolve a song's stream in the background so pressing play starts at once. */
    fun prefetch(id: String) {
        if (urls[id] == null) pool.execute { runCatching { audioUrl(id) } }
    }

    private val locks = ConcurrentHashMap<String, Any>()
    private val pool = java.util.concurrent.Executors.newFixedThreadPool(2)

    fun forget(id: String) = urls.remove(id)

    private fun watch(id: String) = "https://www.youtube.com/watch?v=$id"

    private fun List<*>.songs() = filterIsInstance<StreamInfoItem>().map {
        Song(
            id = YoutubeStreamLinkHandlerFactory.getInstance().getId(it.url),
            title = it.name,
            artist = it.uploaderName.orEmpty().removeSuffix(" - Topic").removeSuffix("VEVO").trim(),
            // YT Music art comes as =w120-h120; ask for a bigger square
            thumb = it.thumbnails.maxByOrNull { t -> t.height }?.url.orEmpty().replace(Regex("=w\\d+-h\\d+"), "=w544-h544"),
            duration = it.duration,
        )
    }
}

private object OkDownloader : Downloader() {
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override fun execute(request: Request): Response {
        val b = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
        request.headers().forEach { (k, vs) -> b.removeHeader(k); vs.forEach { b.addHeader(k, it) } }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}
