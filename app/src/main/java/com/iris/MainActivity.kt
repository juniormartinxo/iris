package com.iris

import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private const val CAMERA_DENIED_PROMPT =
    "Iris precisa de permissão para usar a câmera. Toque em Tentar novamente."
private const val CAMERA_DENIED_PERMANENT =
    "Iris precisa da câmera. Libere a permissão nas configurações do aparelho."
private const val PERMISSION_RESUMED = "Permissão concedida."

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
        // Defesa em profundidade contra tap-jacking: descarta toques entregues através de
        // overlays não-confiáveis (SYSTEM_ALERT_WINDOW). Reforça o filtro default do API 31+
        // para um usuário que não tem feedback visual de overlays cobrindo a tela.
        window.decorView.filterTouchesWhenObscured = true
        setContent {
            IrisTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val perms = rememberPermissionsState()
                LaunchedEffect(perms.micGranted) {
                    viewModel.setMicGranted(perms.micGranted)
                }
                LaunchedEffect(perms.micPermanentlyDenied) {
                    viewModel.setMicPermanentlyDenied(perms.micPermanentlyDenied)
                }
                SideEffect {
                    viewModel.onMicRequest = perms.requestMic
                }
                DisposableEffect(Unit) {
                    onDispose { viewModel.onMicRequest = null }
                }
                val app = IrisApp.from(this)
                val ttsAvailable by app.tts.ptBrAvailable.collectAsStateWithLifecycle()
                val sttAvailable = remember {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity)
                }

                PhaseAnnouncer(state = state, tts = app.tts)

                val cameraDeniedMessage = if (perms.cameraPermanentlyDenied) {
                    CAMERA_DENIED_PERMANENT
                } else {
                    CAMERA_DENIED_PROMPT
                }
                var sawCameraDenied by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(perms.cameraGranted, perms.cameraPermanentlyDenied) {
                    if (!perms.cameraGranted) {
                        sawCameraDenied = true
                        app.tts.announceUi(cameraDeniedMessage)
                    } else if (sawCameraDenied) {
                        sawCameraDenied = false
                        app.tts.announceUi(PERMISSION_RESUMED)
                    }
                }

                val cameraRetry = if (perms.cameraPermanentlyDenied) {
                    perms.openSettings
                } else {
                    perms.requestCamera
                }

                when {
                    !perms.cameraGranted ->
                        FatalErrorScreen(message = cameraDeniedMessage, onRetry = cameraRetry)
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
