package com.noir.music

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtTest {
    private fun song(title: String, duration: Long = 200) = Song("x", title, "a", "", duration)

    @Test
    fun filtersRemixesUnlessAsked() {
        assertTrue(Yt.clean(song("Blinding Lights")))
        assertFalse(Yt.clean(song("Blinding Lights (Slowed + Reverb)")))
        assertFalse(Yt.clean(song("Kesariya - Lofi Remix")))
        assertFalse(Yt.clean(song("Song (Lyrics)")))
        assertFalse(Yt.clean(song("Kesariya (From \"Brahmastra\") (Dance Mix)")))
        assertFalse(Yt.clean(song("Kesariya (Synthwave)")))
        assertTrue(Yt.clean(song("Kesariya (From \"Brahmastra\")")))
        assertFalse(Yt.clean(song("1 Hour Mix", 3600)))
        assertTrue(Yt.clean(song("Blinding Lights (Remix)"), query = "blinding lights remix"))
    }

    @Test
    fun tidiesVideoTitles() {
        fun t(title: String, artist: String) = Yt.tidy(Song("x", title, artist, "")).title
        assertEquals("Wadibusa ft. Ohp Sage & Pcee", t("Uncle Waffles & Royal Musiq - Wadibusa ft. Ohp Sage & Pcee (Official Music Video)", "Uncle Waffles"))
        assertEquals("Fast Fast [Ft. Roscosteazy]", t("031Choppa, Al Xapo - Fast Fast [Ft. Roscosteazy] (Visualizer)", "031CHOPPA and Al Xapo"))
        assertEquals("Kesariya - From Brahmastra", t("Kesariya - From Brahmastra", "Arijit Singh"))
    }

    /** Hits the network: proves search, radio and an actual audio byte range work against YouTube today. */
    @Test
    fun liveSearchRadioAndStream() {
        Yt.init()
        val found = Yt.search("The Weeknd Blinding Lights")
        println("search: " + found.take(3))
        assertTrue(found.isNotEmpty())
        val radio = Yt.radio(found.first().id)
        println("radio(${radio.size}): " + radio.take(5).map { "${it.artist} - ${it.title}" })
        assertTrue(radio.isNotEmpty())
        val url = Yt.audioUrl(found.first().id)
        // Same kind of request ExoPlayer's DefaultHttpDataSource makes
        val r = OkHttpClient().newCall(
            okhttp3.Request.Builder().url(url).header("Range", "bytes=0-4095").header("User-Agent", "AndroidXMedia3/1.4.1").build()
        ).execute()
        println("stream HTTP ${r.code}")
        assertEquals(206, r.code)
    }
}
