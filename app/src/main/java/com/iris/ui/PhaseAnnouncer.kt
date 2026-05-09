package com.iris.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.iris.audio.TtsManager

@Composable
fun PhaseAnnouncer(state: AppState, tts: TtsManager) {
    LaunchedEffect(state.modelVariant) {
        if (state.modelVariant != null) {
            tts.speak("Iris pronta. Toque na tela para descrever, ou use os botões na parte de baixo.")
        }
    }
    LaunchedEffect(state.phase) {
        when (val p = state.phase) {
            is AppPhase.LoadingModel -> tts.speak("Carregando modelo. Aguarde.")
            is AppPhase.FatalError -> tts.speak(p.message)
            else -> {}
        }
    }
}
