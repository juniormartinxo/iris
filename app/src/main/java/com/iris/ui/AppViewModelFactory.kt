package com.iris.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager
import com.iris.util.Prefs

class AppViewModelFactory(
    private val application: Application,
    private val gemma: GemmaManager,
    private val tts: TtsManager,
    private val camera: CameraManager,
    private val speech: SpeechManager,
    private val prefs: Prefs,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        return AppViewModel(application, gemma, tts, camera, speech, prefs) as T
    }
}
