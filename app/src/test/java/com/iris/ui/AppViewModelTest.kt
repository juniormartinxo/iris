package com.iris.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import app.cash.turbine.test
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager
import com.iris.util.Prefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private data class Setup(val vm: AppViewModel, val tts: TtsManager)

    private fun newSetup(
        gemmaState: MutableStateFlow<GemmaManager.ModelState> =
            MutableStateFlow(GemmaManager.ModelState.Ready("E2B")),
    ): Setup {
        val application: Application = mockk(relaxed = true)
        val pm: PowerManager = mockk(relaxed = true)
        every { application.getSystemService(Context.POWER_SERVICE) } returns pm
        every { pm.currentThermalStatus } returns 0

        val gemma: GemmaManager = mockk(relaxed = true)
        val tts: TtsManager = mockk(relaxed = true)
        val camera: CameraManager = mockk(relaxed = true)
        val speech: SpeechManager = mockk(relaxed = true)
        val prefs: Prefs = mockk(relaxed = true)
        every { gemma.state } returns gemmaState
        coEvery { gemma.load() } returns Unit
        every { tts.isSpeaking } returns MutableStateFlow(false)
        every { tts.isReady } returns MutableStateFlow(true)
        every { tts.ptBrAvailable } returns MutableStateFlow(true)
        every { prefs.tutorialDone } returns true
        every { prefs.preflightDone } returns true

        return Setup(
            AppViewModel(application, gemma, tts, camera, speech, prefs),
            tts,
        )
    }

    @Test
    fun selectModeChangesModeWithoutTriggering() = runTest {
        val vm = newSetup().vm
        advanceUntilIdle()
        vm.state.test {
            val initial = awaitItem()
            assertEquals(AppMode.CONTINUOUS, initial.mode)
            assertTrue("VM must be idle before selectMode", initial.phase is AppPhase.Idle)
            vm.selectMode(AppMode.READING)
            val updated = awaitItem()
            assertEquals(AppMode.READING, updated.mode)
            assertTrue("Phase must not become Capturing", updated.phase !is AppPhase.Capturing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectQuestionWithoutMicTriggersMicRequest() = runTest {
        val vm = newSetup().vm
        advanceUntilIdle()
        vm.setMicGranted(false)
        var requested = false
        vm.onMicRequest = { requested = true }
        vm.selectMode(AppMode.QUESTION)
        advanceUntilIdle()
        assertTrue("onMicRequest must fire when mic is missing", requested)
        assertEquals(
            "Mode must not switch to QUESTION while mic is missing",
            AppMode.CONTINUOUS,
            vm.state.value.mode,
        )
    }

    @Test
    fun selectQuestionWithMicPermanentlyDeniedSkipsRequest() = runTest {
        val vm = newSetup().vm
        advanceUntilIdle()
        vm.setMicGranted(false)
        vm.setMicPermanentlyDenied(true)
        var requested = false
        vm.onMicRequest = { requested = true }
        vm.selectMode(AppMode.QUESTION)
        advanceUntilIdle()
        assertFalse("onMicRequest must NOT fire when permanently denied", requested)
    }

    @Test
    fun selectModeAnnouncesMode() = runTest {
        val setup = newSetup()
        advanceUntilIdle()
        setup.vm.selectMode(AppMode.READING)
        advanceUntilIdle()
        coVerify { setup.tts.announceUi(match { it.contains("Modo Leitura") }) }
    }

    @Test
    fun selectModeFirstTimeFullThenShort() = runTest {
        val setup = newSetup()
        advanceUntilIdle()
        setup.vm.selectMode(AppMode.READING)
        advanceUntilIdle()
        setup.vm.selectMode(AppMode.CONTINUOUS)
        advanceUntilIdle()
        setup.vm.selectMode(AppMode.READING)
        advanceUntilIdle()
        coVerify { setup.tts.announceUi("Modo Leitura selecionado. Lê em voz alta o texto da imagem.") }
        coVerify { setup.tts.announceUi("Modo Leitura.") }
    }

    @Test
    fun phaseFollowsGemmaState() = runTest {
        val gemmaState = MutableStateFlow<GemmaManager.ModelState>(GemmaManager.ModelState.Loading)
        val vm = newSetup(gemmaState).vm
        vm.state.test {
            val first = awaitItem()
            assertTrue(first.phase is AppPhase.LoadingModel)
            gemmaState.value = GemmaManager.ModelState.Ready("E2B")
            val second = awaitItem()
            assertTrue(second.phase is AppPhase.Idle)
            assertEquals("E2B", second.modelVariant)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
