package com.iris

import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iris.audio.SpeechManager
import com.iris.camera.CameraManager
import com.iris.permissions.rememberPermissionsState
import com.iris.ui.AppPhase
import com.iris.ui.AppViewModel
import com.iris.ui.AppViewModelFactory
import com.iris.ui.MainScreen
import com.iris.ui.PhaseAnnouncer
import com.iris.ui.screens.FatalErrorScreen
import com.iris.ui.screens.LoadingScreen
import com.iris.ui.screens.ModelMissingScreen
import com.iris.ui.screens.PreflightScreen
import com.iris.ui.screens.TutorialOverlay
import com.iris.ui.theme.IrisTheme
import com.iris.util.Prefs

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        val app = IrisApp.from(this)
        AppViewModelFactory(
            application = application,
            gemma = app.gemma,
            tts = app.tts,
            camera = CameraManager(this, this),
            speech = SpeechManager(this),
            prefs = Prefs(this),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IrisTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val perms = rememberPermissionsState()
                val app = IrisApp.from(this)
                val ttsAvailable by app.tts.ptBrAvailable.collectAsStateWithLifecycle()
                val sttAvailable = remember {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity)
                }

                PhaseAnnouncer(state = state, tts = app.tts)

                when {
                    !perms.cameraGranted ->
                        FatalErrorScreen(
                            message = "Iris precisa de permissão para usar a câmera. Vou pedir agora.",
                            onRetry = { /* permission re-requested by Permissions composable */ },
                        )
                    !state.preflightDone && (!ttsAvailable || !sttAvailable) ->
                        PreflightScreen(
                            ttsAvailable = ttsAvailable,
                            sttAvailable = sttAvailable,
                            onSkip = { viewModel.markPreflightDone() },
                        )
                    state.phase is AppPhase.LoadingModel -> LoadingScreen()
                    state.phase is AppPhase.FatalError -> {
                        val msg = (state.phase as AppPhase.FatalError).message
                        if (msg.contains("FILE_NOT_FOUND")) {
                            ModelMissingScreen(onRetry = { viewModel.retryLoad() })
                        } else {
                            FatalErrorScreen(message = msg, onRetry = { viewModel.retryLoad() })
                        }
                    }
                    !state.tutorialDone ->
                        TutorialOverlay(tts = app.tts, onFinish = { viewModel.markTutorialDone() })
                    else ->
                        MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
