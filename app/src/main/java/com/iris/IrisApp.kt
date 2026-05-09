package com.iris

import android.app.Application
import android.content.Context
import com.iris.ai.GemmaManager
import com.iris.audio.TtsManager
import com.iris.util.CrashLog

class IrisApp : Application() {

    lateinit var gemma: GemmaManager
        private set

    lateinit var tts: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        gemma = GemmaManager(applicationContext)
        tts = TtsManager(applicationContext)
    }

    companion object {
        fun from(context: Context): IrisApp =
            context.applicationContext as IrisApp
    }
}
