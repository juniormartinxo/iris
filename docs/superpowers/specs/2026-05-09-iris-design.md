# Iris — Visual Assistant for the Visually Impaired (Design Spec)

**Date:** 2026-05-09
**Author:** Junior Martins (amjr.box@gmail.com)
**Context:** DEV.to Gemma 4 Challenge submission — deadline 2026-05-24
**Status:** Approved for implementation planning

---

## Pitch

Iris is an offline Android app that turns the phone into an "eye" for blind and low-vision users. The user points the back camera at something, taps the screen, and Iris narrates what it sees in Portuguese. Everything runs on-device — Gemma 4 multimodal weights live on the phone, no internet calls, no cloud round-trips.

Three modes change only the system prompt sent to the model:
- **Continuous** — describes the scene (obstacles, objects, signs).
- **Question** — user asks a specific question by voice; Iris answers.
- **Reading** — full OCR-narration of any visible text.

---

## Target devices and performance budget

Built and tested on **Galaxy S21** (Snapdragon 888 / Exynos 2100, 8GB RAM) and **Galaxy A55** (Exynos 1480, 8GB RAM). CPU backend by default — Mali GPUs on these devices have inconsistent LiteRT-LM driver support.

Expected latency per inference (CPU, INT4 weights, 512px image, max 100 tokens):

| Device | E2B (default) | E4B (fallback) |
|---|---|---|
| Galaxy S21 | 5–9 s | 12–18 s |
| Galaxy A55 | 7–11 s | 15–22 s |

Perceived latency is much lower thanks to streaming TTS — first audible word arrives in approximately **3–5 s on S21** and **4–7 s on A55** with E2B (vision tower + first sentence buffered to first punctuation, ~10 tokens).

---

## Stack

- Kotlin 2.0, Jetpack Compose (BOM 2025.01+)
- minSdk 31 (Android 12) — required for `SpeechRecognizer.createOnDeviceSpeechRecognizer()`
- targetSdk 35, compileSdk 35
- ABI filter: `arm64-v8a` only (LiteRT-LM Gemma 4 ships only for 64-bit ARM)
- MediaPipe `com.google.mediapipe:tasks-genai:0.10.27`
- CameraX 1.4.x (`camera-camera2`, `camera-lifecycle`, `camera-view`)
- AndroidX Lifecycle ViewModel + StateFlow + Compose
- Kotlin Coroutines 1.9+

**Model file**: `gemma-4-E2B-it.litertlm` (preferred default for snappier UX on S21/A55) or `gemma-4-E4B-it.litertlm` (fallback if E2B is missing), sideloaded via `adb push` to `/sdcard/Android/data/com.iris/files/`.

**Source**: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` and `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm`.

---

## Architecture

Single Activity, single Composable screen, MVVM. Most components are scoped to the Application because the model load is expensive and cannot survive Activity recreation.

```
┌──────────────────────────────────────────┐
│ IrisApp : Application                    │
│   ├── GemmaManager      (load 1×)        │
│   └── TtsManager        (init 1×)        │
└──────────────┬───────────────────────────┘
               │ injected via Application.get*()
               ▼
┌──────────────────────────────────────────┐
│ MainActivity                             │
│   └── MainScreen (Composable)            │
│        ├── observes AppViewModel.state   │
│        └── invokes AppViewModel.action() │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ AppViewModel                             │
│   ├── CameraManager  (Activity lifecycle)│
│   ├── SpeechManager  (Activity lifecycle)│
│   ├── ref. GemmaManager (app-scoped)     │
│   ├── ref. TtsManager   (app-scoped)     │
│   └── _state: MutableStateFlow<AppState> │
└──────────────────────────────────────────┘
```

**Threading**:
- Inference runs on `Dispatchers.Default` inside `viewModelScope`.
- Camera capture uses CameraX's executor.
- TTS callbacks land on the main thread (default).

**Idempotency**:
- Tap during inference is ignored (`phase != Idle` blocks).
- Tap during narration cancels the current TTS and inference, then starts fresh.

---

## Components

### File layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/iris/
│   ├── IrisApp.kt                  ← Application; holds singletons
│   ├── MainActivity.kt
│   ├── camera/
│   │   ├── CameraManager.kt        ← bind, captureFrame, assess quality
│   │   └── FrameQuality.kt         ← brightness/variance/blur scoring
│   ├── ai/
│   │   ├── GemmaManager.kt         ← LiteRT-LM lifecycle + streaming describe
│   │   ├── GemmaConfig.kt          ← constants (model paths, max tokens, etc.)
│   │   ├── SystemPrompts.kt        ← per-mode prompts in PT-BR
│   │   └── SentenceBuffer.kt       ← splits token stream into TTS-ready phrases
│   ├── audio/
│   │   ├── TtsManager.kt           ← TextToSpeech wrapper, PT-BR
│   │   └── SpeechManager.kt        ← on-device STT, PT-BR
│   ├── permissions/
│   │   └── Permissions.kt          ← thin Compose helpers for permission flow
│   └── ui/
│       ├── AppState.kt             ← AppMode + AppPhase + AppState
│       ├── AppViewModel.kt
│       ├── MainScreen.kt
│       ├── screens/
│       │   ├── LoadingScreen.kt
│       │   ├── ModelMissingScreen.kt
│       │   ├── PermissionDeniedScreen.kt
│       │   ├── FatalErrorScreen.kt
│       │   └── TutorialOverlay.kt
│       └── theme/
│           ├── Theme.kt            ← high-contrast dark theme
│           └── Type.kt             ← min 18sp typography
├── res/
│   ├── values/
│   │   ├── strings.xml             ← every TTS message + UI label
│   │   └── themes.xml
│   ├── drawable/                   ← 3 mode icons + app icon
│   └── mipmap-*                    ← launcher icons
└── docs/
    ├── superpowers/specs/2026-05-09-iris-design.md
    └── manual-test-plan.md         ← created during implementation
```

### Public contracts

**`GemmaManager`**

```kotlin
class GemmaManager(private val context: Context) {
    val state: StateFlow<ModelState>

    sealed interface ModelState {
        data object NotLoaded : ModelState
        data object Loading : ModelState
        data class Ready(val variant: String) : ModelState
        data class Error(val reason: ErrorReason) : ModelState
    }

    enum class ErrorReason {
        FILE_NOT_FOUND, OOM_DURING_LOAD, CORRUPT_MODEL, INIT_FAILED
    }

    suspend fun load()
    suspend fun describe(
        bitmap: Bitmap,
        mode: AppMode,
        userQuestion: String? = null,
    ): Flow<String>          // emits TTS-ready sentences as model streams tokens
    fun cancelInference()
}
```

**`CameraManager`**

```kotlin
class CameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
) {
    suspend fun bind(previewView: PreviewView)
    suspend fun captureFrame(): Bitmap   // 512px max longest edge, EXIF-rotated
    fun assess(bitmap: Bitmap): FrameQuality
    fun unbind()
}

data class FrameQuality(
    val brightness: Float,   // 0..1
    val variance: Float,     // 0..1
    val blurScore: Float,    // Laplacian variance, normalized
)
```

**`TtsManager`**

```kotlin
class TtsManager(private val context: Context) {
    val isSpeaking: StateFlow<Boolean>
    val isReady: StateFlow<Boolean>
    val ptBrAvailable: StateFlow<Boolean>

    suspend fun speak(text: String)   // suspends until utterance completes
    fun stop()
    fun shutdown()
}
```

**`SpeechManager`**

```kotlin
class SpeechManager(private val context: Context) {
    sealed interface SpeechResult {
        data class Recognized(val text: String) : SpeechResult
        data object NoInput : SpeechResult
        data object NoOfflineModel : SpeechResult
        data class Error(val message: String) : SpeechResult
    }

    suspend fun listen(timeoutMs: Long = 10_000): SpeechResult
    fun cancel()
}
```

**`AppViewModel`**

```kotlin
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<AppState>

    fun selectMode(mode: AppMode)
    fun trigger()
    fun retryLoad()
    fun openTtsSettings()
    fun openSpeechSettings()
    fun replayTutorial()
}
```

### Configuration constants

```kotlin
// ai/GemmaConfig.kt
object GemmaConfig {
    const val PRIMARY_MODEL  = "gemma-4-E2B-it.litertlm"
    const val FALLBACK_MODEL = "gemma-4-E4B-it.litertlm"
    const val MAX_NUM_IMAGES = 1            // single image per session (memory)
    const val MAX_TOKENS = 100
    const val TOP_K = 40
    const val TEMPERATURE = 0.3f
    const val IMG_LONGEST_EDGE = 512
    const val INFERENCE_TIMEOUT_MS = 30_000L
}
```

---

## Data flows

### A. App startup

```
T+0      MainActivity.onCreate
         └─► IrisApp singletons already constructed
         └─► AppViewModel.init {
               checkPermissions()
               gemma.load()  ← async, non-blocking
             }

T+0.1    MainScreen renders phase=LoadingModel
         └─► TTS: "Iniciando Iris, aguarde."
         └─► UI: spinner + "Carregando modelo..."

T+0.5    GemmaManager:
         ├─► looks for E4B in getExternalFilesDir(null)
         ├─► falls back to E2B
         └─► both missing → state=Error(FILE_NOT_FOUND)
             UI shows ModelMissingScreen with adb push instructions
             (visual for sighted helper; TTS narrates plain-language version)

T+5–15   LlmInference.createFromOptions(...) completes
         └─► state.modelVariant = "E2B" or "E4B"
         └─► state.phase = Idle
         └─► First-launch only: triggers Tutorial flow (G)
         └─► TTS: "Iris pronta. Toque na tela para descrever."
```

### B. Tap → describe (Continuous or Reading mode)

```
T+0      User taps button or full screen
         └─► viewModel.trigger()

T+0.05   tts.stop()
         phase = Capturing
         └─► TTS: "Analisando..."   (<200ms latency)
         └─► camera.captureFrame() in parallel

T+0.3    Bitmap returned (≤512px), rotated by EXIF
         └─► quality = camera.assess(bitmap)
         └─► branch:
              brightness < 0.05 → "Imagem muito escura..."   (no inference)
              brightness > 0.95 → "Imagem muito clara..."    (no inference)
              variance < 0.01   → "Sem detalhes na imagem..." (no inference)
              blurScore < TH    → "Imagem desfocada..."      (no inference)
              else              → proceed

T+0.4    phase = Inferring
         gemma.describe(bitmap, mode).collect { sentence ->
           tts.speak(sentence)
         }

T+3–7    First sentence arrives → TTS starts
T+5–10   Subsequent sentences chained
T+7–14   Flow completes (done=true)
         lastDescription = full concatenated text
         phase = Speaking until TTS drains
         phase = Idle
```

### C. Tap → ask (Question mode, with voice)

```
T+0      User taps mic button (or selects QUESTION + taps screen)
         └─► viewModel.selectMode(QUESTION); viewModel.trigger()

T+0.05   phase = Listening
         └─► TTS: "Faça sua pergunta."  (~1s, blocking)
         └─► after TTS done: speech.listen(10s timeout)

T+1      Mic active. UI shows pulsing mic indicator.

T+5      SpeechResult.Recognized("tem uma escada à frente?")
         └─► proceeds to flow B with userQuestion

T+5.1    "Analisando..." → captureFrame → describe(...)
         (rest is identical to flow B)
```

### D. Mode switch without trigger

Long press (>500ms) on a mode button → `selectMode()` only, no `trigger()`. TTS confirms: *"Modo Leitura selecionado."*

Short tap = select + trigger (single gesture).

### E. Re-tap during narration

```
TTS narrating sentence 3 of 5...

T+X      User taps again
         └─► tts.stop() (immediate cut)
         └─► viewModelScope.coroutineContext[Job]?.cancelChildren()
         └─► gemma.cancelInference()
         └─► restart flow B from scratch
```

### F. Inference timeout (>30s)

```
T+30     withTimeout fires
         └─► gemma.cancelInference()
         └─► TTS: "Demorando demais, tente de novo."
         └─► phase = Idle
```

### G. Tutorial (first launch only)

Flagged via SharedPreferences `tutorialDone`. Runs after model is `Ready`:

```
TTS: "Bem-vindo ao Iris. Iris é seu olho digital."
TTS: "A câmera fica nas costas do celular. Segure normalmente,
      com a tela voltada para o seu rosto."
TTS: "Para descrever o que está à sua frente, mantenha o celular
      vertical, com a parte de baixo apontando para o chão."
TTS: "Para ler um texto, deite o celular paralelo ao papel,
      com a tela voltada para cima."
TTS: "Vamos fazer um teste. Aponte para qualquer direção e
      toque duas vezes em qualquer lugar da tela."
[waits up to 15s for tap]
[runs a real description if user taps]
TTS: "Foi essa direção que queria? Se sim, toque uma vez.
      Se não, ajuste e toque duas vezes."
```

User can interrupt anytime by tapping. After completion, `tutorialDone=true`. Repeatable via triple-tap from anywhere.

### H. Pre-flight checks (first launch only)

```
1) tts.ptBrAvailable.value == false?
   → Open Settings.ACTION_TTS_SETTINGS, narrate explanation
2) SpeechRecognizer.isOnDeviceRecognitionAvailable(context) == false?
   → Open Settings.ACTION_VOICE_INPUT_SETTINGS, narrate explanation
3) Mark preflightDone=true
```

---

## Accessibility-first UX

The user is potentially blind. Audio is the primary channel. Visual UI exists for:
1. Hackathon judges watching the demo video.
2. A sighted helper assisting with one-time setup (sideloading the model, installing voice packs).
3. Developer debugging.

### Rules baked into the design

- **No visual references** in TTS messages: never "above", "in the corner", "on the right", "look at the screen".
- **Always actionable**: every error message ends with a concrete action ("toque duas vezes", "ajuste o celular").
- **TalkBack convention**: instructions say "toque duas vezes" because TalkBack's single-tap is "focus", double-tap is "activate".
- **Direction in descriptions**: the Continuous prompt asks the model to begin with "À frente vejo...", "Para baixo vejo...", etc., so the user can tell whether they aimed correctly.

### Compose details

- Whole screen has a `clickable` hit area triggering `viewModel.trigger()` — easy for unsighted finger placement.
- Three large mode buttons (≥80dp height) with rich `contentDescription` in PT-BR.
- `Modifier.clearAndSetSemantics { }` on the camera `PreviewView` so TalkBack does not announce visual debris.
- `LaunchedEffect(state.phase)` triggers TTS announcements automatically on every phase transition.
- `IrisTheme`: dark color scheme (background `#000000`, primary `#FFD600`, on-primary `#000000`, on-background `#FFFFFF`), min font size 18sp.

---

## "Where to point the camera?" — design response

Iris cannot guide camera aim in real time without becoming a different app (continuous-vision narrator). Three mechanisms collaborate to make blind aim usable:

1. **Pre-inference quality check** (~150 ms, no model). Detects dark / overexposed / uniform / blurry frames and gives a corrective TTS message instead of running inference.
2. **Prompt-level ambiguity detection**. The system prompt instructs Gemma 4 to respond with `"Câmera apontada para [parede/teto/chão]. Reaponte."` when the frame contains only a featureless surface — rather than confabulating a description.
3. **Direction prefix in descriptions**. Every Continuous-mode response opens with the apparent direction (`"À frente vejo..."`), so the user immediately knows whether their aim matched intent.
4. **Tutorial calibration**. First launch teaches phone orientation per mode and offers a real test shot.

Pretending the app could guide aim continuously would set a false expectation. Honest design: accept that the user iterates, and make iteration fast and informative.

---

## Error handling

All errors are narrated via TTS in PT-BR. Visual screens are secondary.

| Scenario | Recovery | TTS narration | UI |
|---|---|---|---|
| Model files absent | Manual (sideload) | "O modelo de inteligência ainda não foi instalado. É preciso conectar o celular a um computador uma única vez. Peça ajuda a uma pessoa vidente, ou toque duas vezes para ouvir o passo a passo completo." | `ModelMissingScreen` |
| Model file corrupt | Retry button | "Erro ao carregar o modelo. Toque duas vezes para tentar novamente." | `FatalErrorScreen` |
| OOM during model load (E4B) | Auto-fallback to E2B once | "Memória insuficiente. Trocando para versão menor." | brief overlay |
| OOM during model load (E2B) | None — fatal | "Memória insuficiente para carregar Iris neste celular." | `FatalErrorScreen` |
| OOM during inference | None — manual retry | "Memória cheia. Aguarde dez segundos e toque de novo." | back to Idle |
| Camera permission denied | Re-request via system dialog | "Iris precisa de permissão para usar a câmera. Vou pedir agora." | `PermissionDeniedScreen` |
| Mic permission denied | Question mode disabled, others fine | "Modo pergunta indisponível. Os outros modos funcionam normalmente." | banner on mode bar |
| Frame capture failed | Auto-retry once | "Erro na câmera, tentando de novo." | back to Idle |
| Inference timeout (30s) | Manual retry | "Demorando demais, toque para tentar de novo." | back to Idle |
| TTS engine missing PT-BR | Open `ACTION_TTS_SETTINGS` | "A voz em português offline não está instalada neste celular. Vou abrir as configurações; quando chegar lá, peça ajuda a uma pessoa vidente para baixar o pacote." | overlay with action |
| STT no offline package | Open `ACTION_VOICE_INPUT_SETTINGS` | "O reconhecimento de voz offline em português ainda não está instalado. Vou abrir as configurações." | overlay with action |
| STT no result | Manual retry | "Não entendi. Toque duas vezes para falar de novo." | back to Idle |
| Re-tap during inference | Cancel + restart | (cuts narration, starts fresh "Analisando...") | reset pipeline |
| App backgrounded mid-inference | Cancel silently, release camera | (no narration) | state cleared on return |
| Thermal critical | Block trigger 30s | "O aparelho está aquecendo. Aguarde alguns segundos." | banner |

### Detection mechanisms

- **Model absent**: `File.exists()` at load time.
- **OOM at load**: try/catch around `LlmInference.createFromOptions()`. E4B → E2B fallback once.
- **OOM at runtime**: error callback of `generateResponseAsync()`.
- **Inference stuck**: `withTimeout(30_000) { ... }` in `viewModelScope.launch`. On timeout, calls `gemma.cancelInference()`.
- **Thermal**: `PowerManager.currentThermalStatus` checked before each `trigger()`. If `>= THERMAL_STATUS_CRITICAL`, blocks.
- **TTS PT-BR availability**: `tts.isLanguageAvailable(Locale("pt", "BR"))` at init.
- **STT offline package**: `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` at init.

### Logging

No analytics. No network. `android.util.Log` only when `BuildConfig.DEBUG`. Fatal errors append a stack trace to `getExternalFilesDir(null)/iris_crash.txt` for `adb pull` debugging.

---

## Testing strategy

Pragmatic for the 24-May deadline: **unit tests on pure logic, manual checklist for everything else**.

### Unit tests (~15 tests)

- **`SystemPromptsTest`** — per-mode content, direction prefix, ambiguous-frame instruction, max-3-sentences clause, PT-BR locale.
- **`FrameQualityTest`** — black/white/uniform/blurry/normal bitmaps each produce expected scores.
- **`SentenceBufferTest`** — token stream splits correctly at `.`, `?`, `!`, `\n`; `done=true` flushes remainder.
- **`AppViewModelTest`** — phase transitions are correct; `trigger()` is idempotent during inference.

Stack: JUnit 4 + MockK + Turbine (StateFlow assertions) + Robolectric (only for Bitmap-handling tests).

### Instrumented tests (1 test)

`MainActivityE2ETest` — smoke test on emulator: app opens, permissions auto-granted, `LoadingModel` or `ModelMissingScreen` renders, mode buttons have non-null `contentDescription`.

### Manual test plan (`docs/manual-test-plan.md`)

Executed on both S21 and A55 before submission. Covers:

- **Setup**: APK install, model sideload (E2B-only, E4B-only, both, neither).
- **Inference**: each mode produces plausible output; streaming TTS starts before completion.
- **Quality checks**: covered lens, blank wall, shaken capture each give the right corrective message.
- **Errors**: each row of the error table above, manually triggered.
- **Accessibility (TalkBack ON)**: every button announces correctly; double-tap activates; preview is silent; tutorial is interruptible.
- **Performance budgets**: cold-start, first-word latency, full-response latency on each device.
- **Battery/thermal**: 10 consecutive inferences without `THERMAL_STATUS_CRITICAL` on S21.
- **Pre-flight**: TTS settings + voice settings open correctly when packages are missing.

### Deliberately not tested

- MediaPipe internals (boilerplate, low ROI for a prototype).
- Description quality vs ground truth (subjective).
- Devices outside S21/A55.
- Coverage percentage (weak metric; behavior is what matters).

---

## Open questions / V2 backlog

These are explicitly **out of scope** for the 24-May submission but documented for follow-up:

- **True continuous narration mode**: the original spec called for adaptive-loop narration. Deferred to V2 because demo predictability matters more than pitch fidelity for this milestone.
- **GPU backend**: investigate Mali-G68 (A55) and Adreno 660 (S21) drivers for LiteRT-LM GPU offload. Could halve latency.
- **Wake-word activation**: "Iris, descreve" without tapping. Requires a small wake-word model (~5 MB) and a foreground service.
- **Voice command for mode switch**: "modo leitura" instead of long-press. Adds complexity.
- **Offline language pack bootstrapper**: today we open Settings; could embed our own download flow.
- **Whisper.cpp STT fallback**: removes dependency on Google's offline voice pack. Adds ~75 MB.

---

## Submission deliverables (24-May)

1. **APK** — signed, installable on Android 12+.
2. **README.md** — setup steps including the exact `adb push` command and the URLs for sideloading the model.
3. **Demo video** — 2-3 minutes, ideally narrated by a real low-vision user, showing all 3 modes and the quality-check feedback.
4. **DEV.to article** — explains motivation, architecture, and on-device-AI choices.
5. **Source repo (GitHub)** — public, MIT or Apache 2.0.
6. **`docs/manual-test-plan.md`** — completed checklist with results from S21 and A55.
