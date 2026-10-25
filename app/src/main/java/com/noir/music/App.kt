package com.noir.music

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Yt.init()
        Taste.init(this)
        // The first stream lookup also downloads YouTube's player script (slow); do it now, on the song you're most likely to replay
        (Taste.history + Taste.liked).firstOrNull()?.let { Yt.prefetch(it.id) }
    }
}
