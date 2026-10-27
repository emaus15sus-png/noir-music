package com.noir.music

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TasteTest {
    @Test
    fun playlistsNeverLoseSongs() {
        Taste.init(ApplicationProvider.getApplicationContext())
        val a = Song("a", "A", "x", "", 200)
        val b = Song("b", "B", "x", "")

        Taste.addToPlaylist("Mix", a)
        Taste.addToPlaylist("Mix", a) // already there: no duplicate
        Taste.addToPlaylist("Mix", null) // "New playlist" with a name in use must not wipe it
        assertEquals(listOf(a), Taste.playlists["Mix"])

        Taste.addToPlaylist("Mix", b)
        assertEquals(listOf(a, b), Taste.playlists["Mix"])
        assertEquals("Mix 2", Taste.freeName("Mix"))

        Taste.setPlaylist("Mix", null)
        assertFalse("Mix" in Taste.playlists)
    }
}
