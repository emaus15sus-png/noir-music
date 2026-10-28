package com.noir.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodTest {
    @Test
    fun readsMoodFromWhatYouSay() {
        assertEquals(Mood.Happy to "", Mood.detect("I'm feeling really happy today"))
        assertEquals(Mood.Sad to "", Mood.detect("feeling lonely tonight")) // time words are not search words
        assertEquals(Mood.Sad, Mood.detect("not happy at all").first)
        assertEquals(Mood.Sad, Mood.detect("main khush nahi hoon").first)
        assertEquals(Mood.Sad, Mood.detect("feeling lonely and I miss her").first)
        assertEquals(Mood.Energetic, Mood.detect("going for a workout").first) // not Focus, though it starts with "work"
        assertEquals(Mood.Romantic to "old 90s bollywood", Mood.detect("old 90s bollywood romance"))
        assertEquals(null to "arijit singh", Mood.detect("play some arijit singh"))
    }

    @Test
    fun guessesMoodFromTimeAndLearnsFromPicks() {
        assertEquals(Mood.Happy, Mood.auto(8) { _, _ -> 0 })
        assertEquals(Mood.Chill, Mood.auto(23) { _, _ -> 0 })
        // Someone who keeps picking Romantic at night gets Romantic at night
        assertEquals(Mood.Romantic, Mood.auto(23) { s, m -> if (s == 3 && m == Mood.Romantic) 5 else 0 })
    }

    @Test
    fun interleavesSources() {
        assertEquals(listOf(1, 10, 2, 20, 3), Mood.interleave(listOf(listOf(1, 2, 3), listOf(10, 20))))
    }
}
