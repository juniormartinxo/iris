# Iris Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Commits are owned by the user.** Each task ends with a "stop here, show user the commit command, do not run `git commit`" step. The user runs every commit themselves. Do not bypass this.

**Goal:** Ship Iris — an offline Android app that turns the phone into an "eye" for blind/low-vision users — for the DEV.to Gemma 4 Challenge by 2026-05-24.

**Architecture:** Single Activity + Compose, MVVM with `AppViewModel` + `StateFlow`. Application-scoped `GemmaManager` + `TtsManager` (load once, survive Activity recreation). Activity-scoped `CameraManager` + `SpeechManager`. Inference is on-device via MediaPipe LiteRT-LM (Gemma 4 E2B preferred, E4B fallback). Streaming token-to-sentence buffer feeds TTS so the user hears the first word in 3-7s instead of 8-15s.

**Tech Stack:**
- Kotlin 2.0, Jetpack Compose (BOM 2025.01.01), Material3
- minSdk 31, targetSdk 35, compileSdk 35, ABI `arm64-v8a`
- MediaPipe `com.google.mediapipe:tasks-genai:0.10.27`
- CameraX 1.4.1
- AndroidX Lifecycle 2.8.7
- Kotlin Coroutines 1.9.0
- JUnit 4 + MockK 1.13.13 + Turbine 1.2.0 + Robolectric 4.14 (tests)

**Reference:** [`docs/superpowers/specs/2026-05-09-iris-design.md`](../specs/2026-05-09-iris-design.md)

---

## Task 0: Bootstrap workspace

**Files:**
- Create: `.gitignore`
- Create: `gradle.properties`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle/libs.versions.toml`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
# Android / Gradle
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.apk
*.ap_
*.aab
*.dex
*.class
bin/
gen/
out/

# Model files (sideloaded)
*.litertlm
*.task

# Crash dumps
iris_crash.txt

# IDE
*.swp
*.swo
.vscode/

# Claude internal
.claude/
```

- [ ] **Step 2: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Iris"
include(":app")
```

- [ ] **Step 4: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
compose-bom = "2025.01.01"
activity-compose = "1.9.3"
lifecycle = "2.8.7"
camerax = "1.4.1"
mediapipe-genai = "0.10.27"
coroutines = "1.9.0"
junit = "4.13.2"
mockk = "1.13.13"
turbine = "1.2.0"
robolectric = "4.14"
androidx-test-junit = "1.2.1"
androidx-test-rules = "1.6.1"
espresso = "3.6.1"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camerax-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
mediapipe-tasks-genai = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipe-genai" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-junit" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidx-test-rules" }
espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 5: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 6: Generate Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.11.1 --distribution-type bin`
Expected: creates `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`. If `gradle` is not available system-wide, install via SDKMAN or download manually from `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` and extract. Verify with: `./gradlew --version` — should print Gradle 8.11.1.

- [ ] **Step 7: Stop and tell user to commit**

Show them:
```bash
git add .gitignore gradle.properties settings.gradle.kts build.gradle.kts gradle/libs.versions.toml gradle/wrapper/ gradlew gradlew.bat
git commit -m "chore: bootstrap gradle workspace"
```

Do NOT run `git commit`. Wait for user confirmation that the commit is in before moving to Task 1.

---

## Task 1: App module skeleton

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/iris/IrisApp.kt`
- Create: `app/src/main/java/com/iris/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.iris"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iris"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
```

- [ ] **Step 2: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature
        android:name="android.hardware.camera"
        android:required="true" />
    <uses-feature
        android:name="android.hardware.microphone"
        android:required="true" />

    <application
        android:name=".IrisApp"
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Iris">

        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.Iris">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

- [ ] **Step 3: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Iris</string>
</resources>
```

- [ ] **Step 4: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Iris" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/black</item>
        <item name="android:navigationBarColor">@android:color/black</item>
    </style>
</resources>
```

- [ ] **Step 5: Create `app/src/main/java/com/iris/IrisApp.kt`**

```kotlin
package com.iris

import android.app.Application

class IrisApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

- [ ] **Step 6: Create `app/src/main/java/com/iris/MainActivity.kt`**

```kotlin
package com.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Placeholder() }
    }
}

@Composable
private fun Placeholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Iris", color = Color.White)
    }
}
```

- [ ] **Step 7: Build to confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If it fails complaining about plugin versions, check `libs.versions.toml` matches your installed Android Studio.

- [ ] **Step 8: Stop and tell user to commit**

Show them:
```bash
git add app/build.gradle.kts app/src/main/
git commit -m "feat: app module skeleton with empty MainActivity"
```

Do NOT run `git commit`.

---

## Task 2: AppState and AppMode

**Files:**
- Create: `app/src/main/java/com/iris/ui/AppState.kt`

- [ ] **Step 1: Create `app/src/main/java/com/iris/ui/AppState.kt`**

```kotlin
package com.iris.ui

enum class AppMode {
    CONTINUOUS, QUESTION, READING
}

sealed interface AppPhase {
    data object Idle : AppPhase
    data object LoadingModel : AppPhase
    data object Listening : AppPhase
    data object Capturing : AppPhase
    data object Inferring : AppPhase
    data object Speaking : AppPhase
    data class FatalError(val message: String) : AppPhase
}

data class AppState(
    val mode: AppMode = AppMode.CONTINUOUS,
    val phase: AppPhase = AppPhase.LoadingModel,
    val lastDescription: String = "",
    val modelVariant: String? = null,
    val tutorialDone: Boolean = false,
    val preflightDone: Boolean = false,
    val micPermissionDenied: Boolean = false,
)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

Show them:
```bash
git add app/src/main/java/com/iris/ui/AppState.kt
git commit -m "feat: AppState, AppMode, AppPhase data model"
```

Do NOT run `git commit`.

---

## Task 3: SystemPrompts (TDD)

**Files:**
- Create: `app/src/test/java/com/iris/ai/SystemPromptsTest.kt`
- Create: `app/src/main/java/com/iris/ai/SystemPrompts.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.iris.ai

import com.iris.ui.AppMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptsTest {

    @Test
    fun continuousPromptInstructsDirectionPrefix() {
        val prompt = SystemPrompts.forMode(AppMode.CONTINUOUS)
        assertTrue(
            "Continuous prompt should require a direction prefix",
            prompt.contains("À frente vejo") || prompt.contains("direção")
        )
    }

    @Test
    fun continuousPromptCapsLengthAtThreeSentences() {
        val prompt = SystemPrompts.forMode(AppMode.CONTINUOUS)
        assertTrue(
            "Should instruct max 3 sentences",
            prompt.contains("3 frases") || prompt.contains("três frases")
        )
    }

    @Test
    fun questionPromptInjectsUserQuestion() {
        val prompt = SystemPrompts.forMode(AppMode.QUESTION, "tem alguma escada à frente?")
        assertTrue(
            "Question must appear verbatim in the prompt",
            prompt.contains("tem alguma escada à frente?")
        )
    }

    @Test
    fun questionPromptWithoutQuestionFallsBackToGenericInstruction() {
        val prompt = SystemPrompts.forMode(AppMode.QUESTION, null)
        assertFalse(
            "Should not contain a literal null marker",
            prompt.contains("null")
        )
    }

    @Test
    fun readingPromptInstructsTextOrdering() {
        val prompt = SystemPrompts.forMode(AppMode.READING)
        assertTrue(
            "Reading prompt should describe top-to-bottom left-to-right ordering",
            prompt.contains("cima para baixo") && prompt.contains("esquerda para a direita")
        )
    }

    @Test
    fun readingPromptHandlesNoTextCase() {
        val prompt = SystemPrompts.forMode(AppMode.READING)
        assertTrue(
            "Should instruct what to say if no text is visible",
            prompt.contains("Não vejo texto") || prompt.contains("sem texto")
        )
    }

    @Test
    fun allPromptsRequirePortugueseResponse() {
        AppMode.values().forEach { mode ->
            val prompt = SystemPrompts.forMode(mode, "amostra")
            assertTrue(
                "Mode $mode prompt must request PT-BR responses",
                prompt.contains("português") || prompt.contains("Português")
            )
        }
    }

    @Test
    fun allPromptsHaveAmbiguousFrameInstruction() {
        AppMode.values().forEach { mode ->
            val prompt = SystemPrompts.forMode(mode, "amostra")
            assertTrue(
                "Mode $mode must instruct what to say for blank/uniform frames",
                prompt.contains("parede") || prompt.contains("teto") || prompt.contains("Reaponte")
            )
        }
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.ai.SystemPromptsTest"`
Expected: FAIL with "Unresolved reference: SystemPrompts" or compilation error.

- [ ] **Step 3: Implement `SystemPrompts.kt`**

```kotlin
package com.iris.ai

import com.iris.ui.AppMode

object SystemPrompts {

    fun forMode(mode: AppMode, userQuestion: String? = null): String = when (mode) {
        AppMode.CONTINUOUS -> CONTINUOUS
        AppMode.QUESTION -> question(userQuestion)
        AppMode.READING -> READING
    }

    private val CONTINUOUS = """
        Você é Iris, um guia visual para uma pessoa com deficiência visual.
        Descreva a cena em no máximo 3 frases curtas e objetivas, focando em:
        obstáculos imediatos, objetos relevantes, texto visível, pessoas.

        Sempre comece dizendo a direção que está vendo, exatamente assim:
        "À frente vejo..." ou "Para baixo vejo..." ou "Para cima vejo..."
        ou "Para a direita vejo..." ou "Para a esquerda vejo...".

        Se a imagem mostrar APENAS uma superfície sem detalhes (parede, teto,
        chão, céu, área completamente fora de foco), responda EXATAMENTE assim:
        "Câmera apontada para [parede/teto/chão/etc]. Reaponte para o que
        quer ver." Não invente conteúdo que não está claramente visível.

        Responda em português do Brasil.
    """.trimIndent()

    private fun question(userQuestion: String?): String {
        val q = userQuestion?.takeIf { it.isNotBlank() }
            ?: "Descreva o que está à minha frente."
        return """
            Você é Iris, um assistente visual para uma pessoa com deficiência
            visual. O usuário apontou a câmera e fez a seguinte pergunta:

            "$q"

            Responda de forma clara e objetiva em no máximo 3 frases curtas.
            Se a imagem não permitir responder com confiança, diga claramente:
            "Não consigo ver isso na imagem."

            Se a imagem mostrar apenas uma superfície sem detalhes (parede,
            teto, chão, área borrada), responda: "Câmera apontada para
            [parede/teto/chão/etc]. Reaponte para o que quer ver."

            Responda em português do Brasil.
        """.trimIndent()
    }

    private val READING = """
        Você é Iris, um leitor de texto para uma pessoa com deficiência visual.
        Leia todo o texto visível na imagem, em ordem de cima para baixo, da
        esquerda para a direita.

        Se for uma placa ou aviso, comece com "Aviso:".
        Se for um produto, comece com "Produto:" e leia nome e informações
        relevantes.
        Se não houver texto claramente visível na imagem, responda: "Não vejo
        texto. Aproxime mais a câmera ou verifique a iluminação."

        Se a imagem mostrar apenas uma parede ou teto sem texto, diga isso
        diretamente: "Câmera apontada para parede. Reaponte para o texto."

        Não invente palavras que não está vendo. Responda em português do
        Brasil. Máximo 3 frases curtas se o texto for breve; se for um texto
        longo (página inteira, cardápio), pode estender o necessário para ler
        tudo.
    """.trimIndent()
}
```

- [ ] **Step 4: Run the test, confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.ai.SystemPromptsTest"`
Expected: PASS — 8 tests passed.

- [ ] **Step 5: Stop and tell user to commit**

Show them:
```bash
git add app/src/main/java/com/iris/ai/SystemPrompts.kt app/src/test/java/com/iris/ai/SystemPromptsTest.kt
git commit -m "feat(ai): per-mode system prompts in PT-BR with ambiguous-frame handling"
```

Do NOT run `git commit`.

---

## Task 4: GemmaConfig

**Files:**
- Create: `app/src/main/java/com/iris/ai/GemmaConfig.kt`

- [ ] **Step 1: Create `GemmaConfig.kt`**

```kotlin
package com.iris.ai

object GemmaConfig {
    const val PRIMARY_MODEL = "gemma-4-E2B-it.litertlm"
    const val FALLBACK_MODEL = "gemma-4-E4B-it.litertlm"

    const val MAX_NUM_IMAGES = 1
    const val MAX_TOKENS = 100
    const val TOP_K = 40
    const val TEMPERATURE = 0.3f

    const val IMG_LONGEST_EDGE = 512
    const val INFERENCE_TIMEOUT_MS = 30_000L
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

Show them:
```bash
git add app/src/main/java/com/iris/ai/GemmaConfig.kt
git commit -m "feat(ai): GemmaConfig constants"
```

---

## Task 5: SentenceBuffer (TDD)

**Files:**
- Create: `app/src/test/java/com/iris/ai/SentenceBufferTest.kt`
- Create: `app/src/main/java/com/iris/ai/SentenceBuffer.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.iris.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceBufferTest {

    @Test
    fun emitsNothingWithoutTerminator() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Olá", onSentence = { emissions += it })
        buffer.feed(" mundo", onSentence = { emissions += it })
        assertEquals(emptyList<String>(), emissions)
    }

    @Test
    fun emitsAtPeriod() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Olá mundo. Como vai", onSentence = { emissions += it })
        assertEquals(listOf("Olá mundo."), emissions)
    }

    @Test
    fun emitsAtQuestionMark() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Tudo bem? Sim", onSentence = { emissions += it })
        assertEquals(listOf("Tudo bem?"), emissions)
    }

    @Test
    fun emitsAtExclamation() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Cuidado! À frente", onSentence = { emissions += it })
        assertEquals(listOf("Cuidado!"), emissions)
    }

    @Test
    fun emitsMultipleSentencesInSingleFeed() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Frase um. Frase dois! Frase três? Cauda", onSentence = { emissions += it })
        assertEquals(
            listOf("Frase um.", "Frase dois!", "Frase três?"),
            emissions
        )
    }

    @Test
    fun flushEmitsLeftoverWithoutTerminator() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Olá mundo sem ponto", onSentence = { emissions += it })
        buffer.flush(onSentence = { emissions += it })
        assertEquals(listOf("Olá mundo sem ponto"), emissions)
    }

    @Test
    fun flushEmitsNothingIfBufferEmpty() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Frase.", onSentence = { emissions += it })
        buffer.flush(onSentence = { emissions += it })
        assertEquals(listOf("Frase."), emissions)
    }

    @Test
    fun trimsLeadingWhitespaceOfSubsequentSentences() {
        val buffer = SentenceBuffer()
        val emissions = mutableListOf<String>()
        buffer.feed("Um.   Dois.", onSentence = { emissions += it })
        assertEquals(listOf("Um.", "Dois."), emissions)
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.ai.SentenceBufferTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `SentenceBuffer.kt`**

```kotlin
package com.iris.ai

class SentenceBuffer {

    private val terminators = setOf('.', '?', '!', '\n')
    private val builder = StringBuilder()

    fun feed(chunk: String, onSentence: (String) -> Unit) {
        for (c in chunk) {
            builder.append(c)
            if (c in terminators) {
                emit(onSentence)
            }
        }
    }

    fun flush(onSentence: (String) -> Unit) {
        if (builder.isNotBlank()) {
            emit(onSentence)
        } else {
            builder.setLength(0)
        }
    }

    private fun emit(onSentence: (String) -> Unit) {
        val sentence = builder.toString().trim()
        builder.setLength(0)
        if (sentence.isNotEmpty()) {
            onSentence(sentence)
        }
    }
}
```

- [ ] **Step 4: Run the test, confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.ai.SentenceBufferTest"`
Expected: PASS — 8 tests passed.

- [ ] **Step 5: Stop and tell user to commit**

Show them:
```bash
git add app/src/main/java/com/iris/ai/SentenceBuffer.kt app/src/test/java/com/iris/ai/SentenceBufferTest.kt
git commit -m "feat(ai): SentenceBuffer for streaming token-to-TTS"
```

---

## Task 6: FrameQuality (TDD with Robolectric)

**Files:**
- Create: `app/src/test/java/com/iris/camera/FrameQualityTest.kt`
- Create: `app/src/main/java/com/iris/camera/FrameQuality.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.iris.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrameQualityTest {

    private fun solidBitmap(color: Int, w: Int = 100, h: Int = 100): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun stripedBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.color = Color.WHITE
        for (y in 0 until 100 step 4) {
            canvas.drawRect(0f, y.toFloat(), 100f, (y + 2).toFloat(), paint)
        }
        return bmp
    }

    @Test
    fun blackBitmapIsTooDark() {
        val q = FrameQuality.assess(solidBitmap(Color.BLACK))
        assertTrue("brightness should be low: ${q.brightness}", q.brightness < 0.05f)
    }

    @Test
    fun whiteBitmapIsTooBright() {
        val q = FrameQuality.assess(solidBitmap(Color.WHITE))
        assertTrue("brightness should be high: ${q.brightness}", q.brightness > 0.95f)
    }

    @Test
    fun solidColorBitmapHasLowVariance() {
        val q = FrameQuality.assess(solidBitmap(Color.argb(255, 128, 128, 128)))
        assertTrue("variance should be near zero: ${q.variance}", q.variance < 0.01f)
    }

    @Test
    fun stripedBitmapHasHighVariance() {
        val q = FrameQuality.assess(stripedBitmap())
        assertTrue("variance should be substantial: ${q.variance}", q.variance > 0.05f)
    }

    @Test
    fun stripedBitmapHasNonZeroBlurScore() {
        val q = FrameQuality.assess(stripedBitmap())
        assertTrue("blurScore should be positive: ${q.blurScore}", q.blurScore > 0.0f)
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.camera.FrameQualityTest"`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `FrameQuality.kt`**

```kotlin
package com.iris.camera

import android.graphics.Bitmap
import kotlin.math.abs

data class FrameQuality(
    val brightness: Float,
    val variance: Float,
    val blurScore: Float,
) {
    companion object {
        const val BRIGHTNESS_TOO_DARK = 0.05f
        const val BRIGHTNESS_TOO_BRIGHT = 0.95f
        const val VARIANCE_TOO_LOW = 0.01f
        const val BLUR_TOO_LOW = 0.005f

        fun assess(bitmap: Bitmap): FrameQuality {
            val downsample = downsample(bitmap, target = 64)
            val (w, h) = downsample.width to downsample.height
            val pixels = IntArray(w * h)
            downsample.getPixels(pixels, 0, w, 0, 0, w, h)

            var sum = 0.0
            val luminances = FloatArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                luminances[i] = l
                sum += l
            }
            val mean = (sum / pixels.size).toFloat()

            var varianceAccum = 0.0
            for (l in luminances) {
                val d = l - mean
                varianceAccum += d * d
            }
            val variance = (varianceAccum / pixels.size).toFloat()

            // Laplacian variance approximation: average abs(L[x][y] - L[x+1][y])
            // averaged with horizontal+vertical derivative magnitude.
            var laplacianAccum = 0.0
            var count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = luminances[y * w + x]
                    val n = luminances[(y - 1) * w + x]
                    val s = luminances[(y + 1) * w + x]
                    val e = luminances[y * w + x + 1]
                    val ww = luminances[y * w + x - 1]
                    val laplacian = abs(4 * c - n - s - e - ww)
                    laplacianAccum += laplacian * laplacian
                    count++
                }
            }
            val blur = if (count > 0) (laplacianAccum / count).toFloat() else 0f

            if (downsample !== bitmap) downsample.recycle()
            return FrameQuality(brightness = mean, variance = variance, blurScore = blur)
        }

        private fun downsample(src: Bitmap, target: Int): Bitmap {
            val longest = maxOf(src.width, src.height)
            if (longest <= target) return src
            val scale = target.toFloat() / longest
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, w, h, true)
        }
    }
}
```

- [ ] **Step 4: Run the test, confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.camera.FrameQualityTest"`
Expected: PASS — 5 tests passed.

- [ ] **Step 5: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/camera/FrameQuality.kt app/src/test/java/com/iris/camera/FrameQualityTest.kt
git commit -m "feat(camera): FrameQuality scoring (brightness, variance, blur)"
```

---

## Task 7: TtsManager

**Files:**
- Create: `app/src/main/java/com/iris/audio/TtsManager.kt`

(No unit test — integration via app launch.)

- [ ] **Step 1: Implement `TtsManager.kt`**

```kotlin
package com.iris.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class TtsManager(private val context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ptBrAvailable = MutableStateFlow(false)
    val ptBrAvailable: StateFlow<Boolean> = _ptBrAvailable.asStateFlow()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("pt", "BR")
            val langStatus = tts.setLanguage(locale)
            val available = langStatus == TextToSpeech.LANG_AVAILABLE ||
                langStatus == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                langStatus == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            _ptBrAvailable.value = available
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)
            _isReady.value = true
        } else {
            _isReady.value = false
        }
    }

    suspend fun speak(text: String) {
        if (!_isReady.value || text.isBlank()) return
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == id) _isSpeaking.value = true
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                @Deprecated("kept for API compat")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) {
                        _isSpeaking.value = false
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            })
            cont.invokeOnCancellation { tts.stop(); _isSpeaking.value = false }
            val params = Bundle()
            tts.speak(text, TextToSpeech.QUEUE_ADD, params, id)
        }
    }

    fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        _isReady.value = false
        _isSpeaking.value = false
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/audio/TtsManager.kt
git commit -m "feat(audio): TtsManager wrapper with PT-BR locale and offline-pack detection"
```

---

## Task 8: SpeechManager

**Files:**
- Create: `app/src/main/java/com/iris/audio/SpeechManager.kt`

- [ ] **Step 1: Implement `SpeechManager.kt`**

```kotlin
package com.iris.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SpeechManager(private val context: Context) {

    sealed interface SpeechResult {
        data class Recognized(val text: String) : SpeechResult
        data object NoInput : SpeechResult
        data object NoOfflineModel : SpeechResult
        data class Error(val message: String) : SpeechResult
    }

    private var recognizer: SpeechRecognizer? = null

    fun isOfflineRecognitionAvailable(): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    suspend fun listen(timeoutMs: Long = 10_000): SpeechResult =
        suspendCancellableCoroutine { cont ->
            if (!isOfflineRecognitionAvailable()) {
                if (cont.isActive) cont.resume(SpeechResult.NoOfflineModel)
                return@suspendCancellableCoroutine
            }

            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = rec
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1500L)
            }

            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val result = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechResult.NoInput
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechResult.NoOfflineModel
                        else -> SpeechResult.Error("Erro de reconhecimento: $error")
                    }
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }

                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = list?.firstOrNull()?.takeIf { it.isNotBlank() }
                    cleanup()
                    if (cont.isActive) {
                        cont.resume(
                            if (text != null) SpeechResult.Recognized(text)
                            else SpeechResult.NoInput
                        )
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            cont.invokeOnCancellation { cleanup() }
            rec.startListening(intent)
        }

    fun cancel() {
        cleanup()
    }

    private fun cleanup() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/audio/SpeechManager.kt
git commit -m "feat(audio): on-device PT-BR SpeechManager with offline detection"
```

---

## Task 9: CameraManager

**Files:**
- Create: `app/src/main/java/com/iris/camera/CameraManager.kt`

- [ ] **Step 1: Implement `CameraManager.kt`**

```kotlin
package com.iris.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.iris.ai.GemmaConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
) {

    private var imageCapture: ImageCapture? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()

    suspend fun bind(previewView: PreviewView) =
        suspendCancellableCoroutine<Unit> { cont ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    imageCapture = capture
                    if (cont.isActive) cont.resume(Unit)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    suspend fun captureFrame(): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resumeWithException(IllegalStateException("Camera not bound"))
            return@suspendCancellableCoroutine
        }
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toRotatedDownsampledBitmap()
                    if (cont.isActive) cont.resume(bitmap)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resumeWithException(exception)
            }
        })
    }

    fun assess(bitmap: Bitmap): FrameQuality = FrameQuality.assess(bitmap)

    fun unbind() {
        imageCapture = null
        captureExecutor.shutdown()
    }

    private fun ImageProxy.toRotatedDownsampledBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val longest = maxOf(raw.width, raw.height)
        val scaled = if (longest > GemmaConfig.IMG_LONGEST_EDGE) {
            val s = GemmaConfig.IMG_LONGEST_EDGE.toFloat() / longest
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * s).toInt().coerceAtLeast(1),
                (raw.height * s).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== raw) raw.recycle() }
        } else raw

        val rotation = imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
            if (rotated !== scaled) scaled.recycle()
            rotated
        } else scaled
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/camera/CameraManager.kt
git commit -m "feat(camera): CameraManager with ImageCapture, downscale, and EXIF rotation"
```

---

## Task 10: GemmaManager — load() and state machine

**Files:**
- Create: `app/src/main/java/com/iris/ai/GemmaManager.kt`

- [ ] **Step 1: Implement skeleton with load()**

```kotlin
package com.iris.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.iris.ui.AppMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class GemmaManager(private val context: Context) {

    sealed interface ModelState {
        data object NotLoaded : ModelState
        data object Loading : ModelState
        data class Ready(val variant: String) : ModelState
        data class Error(val reason: ErrorReason) : ModelState
    }

    enum class ErrorReason {
        FILE_NOT_FOUND, OOM_DURING_LOAD, CORRUPT_MODEL, INIT_FAILED
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var llm: LlmInference? = null
    private var loadedVariant: String? = null
    @Volatile private var currentSession: LlmInferenceSession? = null

    suspend fun load() = withContext(Dispatchers.Default) {
        _state.value = ModelState.Loading
        val baseDir = context.getExternalFilesDir(null)
        if (baseDir == null || !baseDir.exists()) {
            _state.value = ModelState.Error(ErrorReason.FILE_NOT_FOUND)
            return@withContext
        }

        val candidates = listOf(
            "E2B" to File(baseDir, GemmaConfig.PRIMARY_MODEL),
            "E4B" to File(baseDir, GemmaConfig.FALLBACK_MODEL),
        ).filter { (_, f) -> f.exists() && f.length() > 0 }

        if (candidates.isEmpty()) {
            _state.value = ModelState.Error(ErrorReason.FILE_NOT_FOUND)
            return@withContext
        }

        for ((variant, file) in candidates) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxNumImages(GemmaConfig.MAX_NUM_IMAGES)
                    .build()
                val instance = LlmInference.createFromOptions(context, options)
                llm = instance
                loadedVariant = variant
                _state.value = ModelState.Ready(variant)
                return@withContext
            } catch (oom: OutOfMemoryError) {
                _state.value = ModelState.Error(ErrorReason.OOM_DURING_LOAD)
                continue
            } catch (t: Throwable) {
                _state.value = ModelState.Error(ErrorReason.INIT_FAILED)
                continue
            }
        }
    }

    fun cancelInference() {
        runCatching {
            currentSession?.cancelGenerateResponseAsync()
            currentSession?.close()
        }
        currentSession = null
    }

    suspend fun describe(
        bitmap: Bitmap,
        mode: AppMode,
        userQuestion: String? = null,
    ): Flow<String> = TODO("implemented in Task 11")
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (the `TODO` is allowed — it's a Kotlin stdlib function).

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ai/GemmaManager.kt
git commit -m "feat(ai): GemmaManager.load() with E2B/E4B detection and state machine"
```

---

## Task 11: GemmaManager — describe() with streaming

**Files:**
- Modify: `app/src/main/java/com/iris/ai/GemmaManager.kt:84-90`

- [ ] **Step 1: Replace the `TODO` describe() with the streaming implementation**

Replace the line `suspend fun describe(...): Flow<String> = TODO(...)` with:

```kotlin
suspend fun describe(
    bitmap: Bitmap,
    mode: AppMode,
    userQuestion: String? = null,
): Flow<String> = callbackFlow {
    val llmInstance = llm
    if (llmInstance == null) {
        close(IllegalStateException("Model not loaded"))
        return@callbackFlow
    }

    val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTopK(GemmaConfig.TOP_K)
        .setTemperature(GemmaConfig.TEMPERATURE)
        .setGraphOptions(
            GraphOptions.builder().setEnableVisionModality(true).build()
        )
        .build()

    val session = LlmInferenceSession.createFromOptions(llmInstance, sessionOptions)
    currentSession = session
    val buffer = SentenceBuffer()

    try {
        session.addQueryChunk(SystemPrompts.forMode(mode, userQuestion))
        session.addImage(BitmapImageBuilder(bitmap).build())

        session.generateResponseAsync { partial, done ->
            buffer.feed(partial) { sentence -> trySend(sentence) }
            if (done) {
                buffer.flush { sentence -> trySend(sentence) }
                close()
            }
        }

        awaitClose {
            runCatching { session.cancelGenerateResponseAsync() }
            runCatching { session.close() }
            if (currentSession === session) currentSession = null
        }
    } catch (t: Throwable) {
        runCatching { session.close() }
        if (currentSession === session) currentSession = null
        close(t)
    }
}.flowOn(Dispatchers.Default)
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ai/GemmaManager.kt
git commit -m "feat(ai): GemmaManager.describe() streaming via SentenceBuffer"
```

---

## Task 12: Wire singletons into IrisApp

**Files:**
- Modify: `app/src/main/java/com/iris/IrisApp.kt:1-15`

- [ ] **Step 1: Replace `IrisApp.kt` with full implementation**

```kotlin
package com.iris

import android.app.Application
import com.iris.ai.GemmaManager
import com.iris.audio.TtsManager

class IrisApp : Application() {

    lateinit var gemma: GemmaManager
        private set

    lateinit var tts: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        gemma = GemmaManager(applicationContext)
        tts = TtsManager(applicationContext)
    }

    companion object {
        fun from(context: android.content.Context): IrisApp =
            context.applicationContext as IrisApp
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/IrisApp.kt
git commit -m "feat: wire Gemma and TTS singletons in IrisApp"
```

---

## Task 13: AppViewModel skeleton (TDD on state transitions)

**Files:**
- Create: `app/src/main/java/com/iris/ui/AppViewModel.kt`
- Create: `app/src/test/java/com/iris/ui/AppViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.iris.ui

import app.cash.turbine.test
import com.iris.ai.GemmaManager
import com.iris.audio.TtsManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun selectModeChangesModeWithoutTriggering() = runTest {
        val gemma: GemmaManager = mockk(relaxed = true)
        val tts: TtsManager = mockk(relaxed = true)
        every { gemma.state } returns MutableStateFlow(GemmaManager.ModelState.Ready("E2B"))
        every { tts.isSpeaking } returns MutableStateFlow(false)
        every { tts.isReady } returns MutableStateFlow(true)
        every { tts.ptBrAvailable } returns MutableStateFlow(true)

        val vm = AppViewModel(gemma, tts)
        vm.state.test {
            val initial = awaitItem()
            assertEquals(AppMode.CONTINUOUS, initial.mode)
            vm.selectMode(AppMode.READING)
            val updated = awaitItem()
            assertEquals(AppMode.READING, updated.mode)
            assertTrue("Phase must not become Capturing", updated.phase !is AppPhase.Capturing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun phaseFollowsGemmaState() = runTest {
        val gemmaState = MutableStateFlow<GemmaManager.ModelState>(GemmaManager.ModelState.Loading)
        val gemma: GemmaManager = mockk(relaxed = true)
        val tts: TtsManager = mockk(relaxed = true)
        every { gemma.state } returns gemmaState
        every { tts.isSpeaking } returns MutableStateFlow(false)
        every { tts.isReady } returns MutableStateFlow(true)
        every { tts.ptBrAvailable } returns MutableStateFlow(true)

        val vm = AppViewModel(gemma, tts)
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
```

- [ ] **Step 2: Implement `AppViewModel.kt`**

```kotlin
package com.iris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.ai.GemmaManager
import com.iris.audio.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val gemma: GemmaManager,
    private val tts: TtsManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            gemma.state.collect { gs ->
                _state.update { current ->
                    when (gs) {
                        is GemmaManager.ModelState.NotLoaded ->
                            current.copy(phase = AppPhase.LoadingModel, modelVariant = null)
                        is GemmaManager.ModelState.Loading ->
                            current.copy(phase = AppPhase.LoadingModel)
                        is GemmaManager.ModelState.Ready ->
                            current.copy(
                                phase = AppPhase.Idle,
                                modelVariant = gs.variant,
                            )
                        is GemmaManager.ModelState.Error ->
                            current.copy(
                                phase = AppPhase.FatalError("Erro ao carregar modelo: ${gs.reason}"),
                                modelVariant = null,
                            )
                    }
                }
            }
        }
    }

    fun selectMode(mode: AppMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun trigger() {
        // Implemented in Task 17.
    }

    fun retryLoad() {
        viewModelScope.launch { gemma.load() }
    }
}
```

- [ ] **Step 3: Run the test, confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.ui.AppViewModelTest"`
Expected: PASS — 2 tests passed.

- [ ] **Step 4: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/AppViewModel.kt app/src/test/java/com/iris/ui/AppViewModelTest.kt
git commit -m "feat(ui): AppViewModel skeleton with mode selection and gemma state mirror"
```

---

## Task 14: Theme.kt and Type.kt (high contrast + 18sp+)

**Files:**
- Create: `app/src/main/java/com/iris/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/iris/ui/theme/Type.kt`

- [ ] **Step 1: Create `Type.kt`**

```kotlin
package com.iris.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IrisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
)
```

- [ ] **Step 2: Create `Theme.kt`**

```kotlin
package com.iris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IrisColors = darkColorScheme(
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    primary = Color(0xFFFFD600),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    error = Color(0xFFFF6E6E),
    onError = Color(0xFF000000),
)

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IrisColors,
        typography = IrisTypography,
        content = content,
    )
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/theme/
git commit -m "feat(ui): high-contrast IrisTheme with 18sp+ typography"
```

---

## Task 15: Permissions helper

**Files:**
- Create: `app/src/main/java/com/iris/permissions/Permissions.kt`

- [ ] **Step 1: Implement `Permissions.kt`**

```kotlin
package com.iris.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class PermissionsState(
    val cameraGranted: Boolean,
    val micGranted: Boolean,
)

@Composable
fun rememberPermissionsState(): PermissionsState {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(checkGranted(context, Manifest.permission.CAMERA)) }
    var micGranted by remember { mutableStateOf(checkGranted(context, Manifest.permission.RECORD_AUDIO)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] ?: cameraGranted
        micGranted = result[Manifest.permission.RECORD_AUDIO] ?: micGranted
    }

    LaunchedEffect(Unit) {
        val toRequest = buildList {
            if (!cameraGranted) add(Manifest.permission.CAMERA)
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
        }
        if (toRequest.isNotEmpty()) launcher.launch(toRequest.toTypedArray())
    }

    return PermissionsState(cameraGranted, micGranted)
}

private fun checkGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/permissions/Permissions.kt
git commit -m "feat: Compose permissions helper for camera and mic"
```

---

## Task 16: AppViewModelFactory and Activity-scoped managers

**Files:**
- Create: `app/src/main/java/com/iris/ui/AppViewModelFactory.kt`
- Modify: `app/src/main/java/com/iris/ui/AppViewModel.kt:1-100`

- [ ] **Step 1: Add Activity managers to ViewModel**

Replace the `AppViewModel.kt` constructor and add fields. Open `app/src/main/java/com/iris/ui/AppViewModel.kt` and replace the class declaration through the closing brace with:

```kotlin
package com.iris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    private val gemma: GemmaManager,
    private val tts: TtsManager,
    private val camera: CameraManager,
    private val speech: SpeechManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            gemma.state.collect { gs ->
                _state.update { current ->
                    when (gs) {
                        is GemmaManager.ModelState.NotLoaded ->
                            current.copy(phase = AppPhase.LoadingModel, modelVariant = null)
                        is GemmaManager.ModelState.Loading ->
                            current.copy(phase = AppPhase.LoadingModel)
                        is GemmaManager.ModelState.Ready ->
                            current.copy(phase = AppPhase.Idle, modelVariant = gs.variant)
                        is GemmaManager.ModelState.Error ->
                            current.copy(
                                phase = AppPhase.FatalError("Erro ao carregar modelo: ${gs.reason}"),
                                modelVariant = null,
                            )
                    }
                }
            }
        }
        viewModelScope.launch { gemma.load() }
    }

    fun selectMode(mode: AppMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun trigger() {
        // Implemented in Task 17.
    }

    fun retryLoad() {
        viewModelScope.launch { gemma.load() }
    }
}
```

- [ ] **Step 2: Update `AppViewModelTest.kt` to match new signature**

Replace the contents of `app/src/test/java/com/iris/ui/AppViewModelTest.kt` with:

```kotlin
package com.iris.ui

import app.cash.turbine.test
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newViewModel(
        gemmaState: MutableStateFlow<GemmaManager.ModelState> =
            MutableStateFlow(GemmaManager.ModelState.Ready("E2B")),
    ): Pair<AppViewModel, MutableStateFlow<GemmaManager.ModelState>> {
        val gemma: GemmaManager = mockk(relaxed = true)
        val tts: TtsManager = mockk(relaxed = true)
        val camera: CameraManager = mockk(relaxed = true)
        val speech: SpeechManager = mockk(relaxed = true)
        every { gemma.state } returns gemmaState
        coEvery { gemma.load() } returns Unit
        every { tts.isSpeaking } returns MutableStateFlow(false)
        every { tts.isReady } returns MutableStateFlow(true)
        every { tts.ptBrAvailable } returns MutableStateFlow(true)
        return AppViewModel(gemma, tts, camera, speech) to gemmaState
    }

    @Test
    fun selectModeChangesModeWithoutTriggering() = runTest {
        val (vm, _) = newViewModel()
        vm.state.test {
            val initial = awaitItem()
            assertEquals(AppMode.CONTINUOUS, initial.mode)
            vm.selectMode(AppMode.READING)
            val updated = awaitItem()
            assertEquals(AppMode.READING, updated.mode)
            assertTrue(updated.phase !is AppPhase.Capturing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun phaseFollowsGemmaState() = runTest {
        val gemmaState = MutableStateFlow<GemmaManager.ModelState>(GemmaManager.ModelState.Loading)
        val (vm, _) = newViewModel(gemmaState)
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
```

- [ ] **Step 3: Create `AppViewModelFactory.kt`**

```kotlin
package com.iris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager

class AppViewModelFactory(
    private val gemma: GemmaManager,
    private val tts: TtsManager,
    private val camera: CameraManager,
    private val speech: SpeechManager,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        return AppViewModel(gemma, tts, camera, speech) as T
    }
}
```

- [ ] **Step 4: Run tests, confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.iris.ui.AppViewModelTest"`
Expected: PASS — 2 tests passed.

- [ ] **Step 5: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/ app/src/test/java/com/iris/ui/AppViewModelTest.kt
git commit -m "feat(ui): inject Camera and Speech managers + ViewModel factory"
```

---

## Task 17: trigger() pipeline (the big one)

**Files:**
- Modify: `app/src/main/java/com/iris/ui/AppViewModel.kt:50-75`

- [ ] **Step 1: Replace the `trigger()` placeholder with the full pipeline**

In `AppViewModel.kt`, replace the `fun trigger() { ... }` block with:

```kotlin
private var inFlightJob: kotlinx.coroutines.Job? = null

fun trigger() {
    val current = _state.value
    if (current.phase is AppPhase.Capturing ||
        current.phase is AppPhase.Inferring ||
        current.phase is AppPhase.Listening ||
        current.phase is AppPhase.LoadingModel) {
        // Re-tap during inference cancels and restarts; during loading is ignored.
        if (current.phase is AppPhase.LoadingModel) return
        inFlightJob?.cancel()
        gemma.cancelInference()
        tts.stop()
    }

    inFlightJob = viewModelScope.launch {
        runCatching {
            tts.stop()
            if (current.mode == AppMode.QUESTION) {
                _state.update { it.copy(phase = AppPhase.Listening) }
                tts.speak("Faça sua pergunta.")
                val sr = speech.listen()
                when (sr) {
                    is SpeechManager.SpeechResult.Recognized ->
                        runDescribe(question = sr.text)
                    is SpeechManager.SpeechResult.NoInput -> {
                        tts.speak("Não entendi. Toque duas vezes para falar de novo.")
                        _state.update { it.copy(phase = AppPhase.Idle) }
                    }
                    is SpeechManager.SpeechResult.NoOfflineModel -> {
                        tts.speak(
                            "O reconhecimento de voz offline em português ainda não está " +
                            "instalado neste celular. Vou abrir as configurações."
                        )
                        _state.update { it.copy(phase = AppPhase.Idle) }
                    }
                    is SpeechManager.SpeechResult.Error -> {
                        tts.speak("Erro no reconhecimento de voz. Tente de novo.")
                        _state.update { it.copy(phase = AppPhase.Idle) }
                    }
                }
            } else {
                runDescribe(question = null)
            }
        }.onFailure { t ->
            if (t !is kotlinx.coroutines.CancellationException) {
                tts.speak("Erro inesperado. Tente de novo.")
                _state.update { it.copy(phase = AppPhase.Idle) }
            }
        }
    }
}

private suspend fun runDescribe(question: String?) {
    _state.update { it.copy(phase = AppPhase.Capturing) }
    tts.speak("Analisando.")

    val frame = runCatching { camera.captureFrame() }.getOrElse {
        tts.speak("Erro na câmera. Tente de novo.")
        _state.update { it.copy(phase = AppPhase.Idle) }
        return
    }

    val q = camera.assess(frame)
    val complaint = qualityComplaint(q)
    if (complaint != null) {
        tts.speak(complaint)
        _state.update { it.copy(phase = AppPhase.Idle) }
        return
    }

    _state.update { it.copy(phase = AppPhase.Inferring) }
    val collected = StringBuilder()
    runCatching {
        kotlinx.coroutines.withTimeout(com.iris.ai.GemmaConfig.INFERENCE_TIMEOUT_MS) {
            gemma.describe(frame, _state.value.mode, question).collect { sentence ->
                collected.append(sentence).append(' ')
                tts.speak(sentence)
            }
        }
    }.onFailure { t ->
        when (t) {
            is kotlinx.coroutines.TimeoutCancellationException ->
                tts.speak("Demorando demais, toque duas vezes para tentar de novo.")
            is OutOfMemoryError ->
                tts.speak("Memória cheia. Aguarde dez segundos e toque de novo.")
            !is kotlinx.coroutines.CancellationException ->
                tts.speak("Erro durante a análise. Toque duas vezes para tentar de novo.")
        }
        gemma.cancelInference()
    }

    _state.update {
        it.copy(
            phase = AppPhase.Idle,
            lastDescription = collected.toString().trim(),
        )
    }
}

private fun qualityComplaint(q: com.iris.camera.FrameQuality): String? = when {
    q.brightness < com.iris.camera.FrameQuality.BRIGHTNESS_TOO_DARK ->
        "Imagem muito escura. Verifique a iluminação ou se há algo cobrindo a câmera."
    q.brightness > com.iris.camera.FrameQuality.BRIGHTNESS_TOO_BRIGHT ->
        "Imagem muito clara. Há luz forte direta na câmera."
    q.variance < com.iris.camera.FrameQuality.VARIANCE_TOO_LOW ->
        "Não vejo nada com detalhes. A câmera pode estar apontada para uma parede ou superfície vazia."
    q.blurScore < com.iris.camera.FrameQuality.BLUR_TOO_LOW ->
        "Imagem desfocada. Segure o celular firme e tente de novo."
    else -> null
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run tests to verify nothing broke**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all tests pass.

- [ ] **Step 4: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/AppViewModel.kt
git commit -m "feat(ui): trigger() pipeline with quality check, streaming, timeout, and re-tap cancellation"
```

---

## Task 18: MainScreen — basic layout

**Files:**
- Create: `app/src/main/java/com/iris/ui/MainScreen.kt`

- [ ] **Step 1: Implement `MainScreen.kt`**

```kotlin
package com.iris.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.trigger() }
            .semantics { contentDescription = "Toque duas vezes para descrever a cena no modo atual." }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
            factory = { ctx ->
                PreviewView(ctx).apply { keepScreenOn = true }
            }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ModeBanner(state)
            Spacer(modifier = Modifier.fillMaxHeight(1f).weight(1f))
            DescriptionStrip(state)
            ModeBar(currentMode = state.mode, onSelect = { mode ->
                viewModel.selectMode(mode)
                viewModel.trigger()
            })
        }
    }
}

@Composable
private fun ModeBanner(state: AppState) {
    val label = when (state.mode) {
        AppMode.CONTINUOUS -> "Modo Contínuo"
        AppMode.QUESTION -> "Modo Pergunta"
        AppMode.READING -> "Modo Leitura"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = "Modo atual: $label" }
        )
        Spacer(modifier = Modifier.fillMaxWidth(1f).weight(1f))
        state.modelVariant?.let {
            Text(text = it, color = Color(0xFFFFD600), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DescriptionStrip(state: AppState) {
    if (state.lastDescription.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(16.dp)
    ) {
        Text(
            text = state.lastDescription,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ModeBar(currentMode: AppMode, onSelect: (AppMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ModeButton(
            selected = currentMode == AppMode.CONTINUOUS,
            label = "Contínuo",
            description = "Modo Contínuo: descreve a cena à frente. Toque duas vezes para usar agora.",
            icon = Icons.Filled.Visibility,
            onClick = { onSelect(AppMode.CONTINUOUS) },
        )
        ModeButton(
            selected = currentMode == AppMode.QUESTION,
            label = "Pergunta",
            description = "Modo Pergunta: faz uma pergunta por voz sobre o que está vendo. Toque duas vezes.",
            icon = Icons.Filled.Mic,
            onClick = { onSelect(AppMode.QUESTION) },
        )
        ModeButton(
            selected = currentMode == AppMode.READING,
            label = "Leitura",
            description = "Modo Leitura: lê em voz alta o texto da imagem. Toque duas vezes.",
            icon = Icons.Filled.MenuBook,
            onClick = { onSelect(AppMode.READING) },
        )
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color(0xFFFFD600) else Color(0xFF222222)
    val fg = if (selected) Color.Black else Color.White
    Column(
        modifier = Modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/MainScreen.kt
git commit -m "feat(ui): MainScreen with full-screen tap, mode bar, and description strip"
```

---

## Task 19: Loading, ModelMissing, and FatalError overlay screens

**Files:**
- Create: `app/src/main/java/com/iris/ui/screens/LoadingScreen.kt`
- Create: `app/src/main/java/com/iris/ui/screens/ModelMissingScreen.kt`
- Create: `app/src/main/java/com/iris/ui/screens/FatalErrorScreen.kt`

- [ ] **Step 1: Create `LoadingScreen.kt`**

```kotlin
package com.iris.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "Carregando modelo Iris. Aguarde de quinze a trinta segundos." },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(color = Color(0xFFFFD600))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Carregando Iris...",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Aguarde de 15 a 30 segundos.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
```

- [ ] **Step 2: Create `ModelMissingScreen.kt`**

```kotlin
package com.iris.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ModelMissingScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Modelo não encontrado",
                color = Color(0xFFFF6E6E),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "O modelo de inteligência ainda não foi instalado neste celular. Para usar Iris, é preciso conectar o celular a um computador uma única vez. Peça ajuda a uma pessoa vidente.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Para a pessoa vidente:",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "1. No PC, baixe o modelo:\nhttps://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "2. Conecte o celular via USB e rode:\nadb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFD600))
                    .clickable(onClick = onRetry)
                    .padding(20.dp)
                    .semantics { contentDescription = "Verificar de novo. Toque duas vezes depois de instalar o modelo." },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Verificar novamente",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create `FatalErrorScreen.kt`**

```kotlin
package com.iris.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun FatalErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Erro",
                color = Color(0xFFFF6E6E),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFD600))
                    .clickable(onClick = onRetry)
                    .padding(20.dp)
                    .semantics { contentDescription = "Tentar novamente. Toque duas vezes." },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Tentar novamente",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/screens/
git commit -m "feat(ui): Loading, ModelMissing, and FatalError screens"
```

---

## Task 20: TTS phase announcer

**Files:**
- Create: `app/src/main/java/com/iris/ui/PhaseAnnouncer.kt`

- [ ] **Step 1: Create `PhaseAnnouncer.kt`**

```kotlin
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
        when (state.phase) {
            is AppPhase.LoadingModel -> tts.speak("Carregando modelo. Aguarde.")
            is AppPhase.FatalError -> tts.speak(state.phase.message)
            else -> {} // other phases announce themselves via trigger() flow
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/PhaseAnnouncer.kt
git commit -m "feat(ui): PhaseAnnouncer narrates state transitions via TTS"
```

---

## Task 21: Wire MainActivity to ViewModel + screens

**Files:**
- Modify: `app/src/main/java/com/iris/MainActivity.kt:1-50`

- [ ] **Step 1: Replace `MainActivity.kt`**

```kotlin
package com.iris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
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
import com.iris.ui.theme.IrisTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        val app = IrisApp.from(this)
        AppViewModelFactory(
            gemma = app.gemma,
            tts = app.tts,
            camera = CameraManager(this, this),
            speech = SpeechManager(this),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IrisTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val perms = rememberPermissionsState()
                val app = IrisApp.from(this)

                PhaseAnnouncer(state = state, tts = app.tts)

                when {
                    !perms.cameraGranted ->
                        FatalErrorScreen(
                            message = "Iris precisa de permissão para usar a câmera. Vou pedir agora.",
                            onRetry = { /* re-request handled by Permissions composable */ }
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
                    else -> MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test**

Install on connected device:
Run: `./gradlew :app:installDebug`
Expected: APK installs. Open app — without model file present, you should see the `ModelMissingScreen` with the adb push command displayed. TTS should read out "Modelo não encontrado..." (PT-BR voice required).

- [ ] **Step 4: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/MainActivity.kt
git commit -m "feat: wire MainActivity with ViewModel, screens, and phase announcer"
```

---

## Task 22: Tutorial overlay

**Files:**
- Create: `app/src/main/java/com/iris/ui/screens/TutorialOverlay.kt`
- Modify: `app/src/main/java/com/iris/ui/AppViewModel.kt:25-50`
- Modify: `app/src/main/java/com/iris/MainActivity.kt:40-60`

- [ ] **Step 1: Create `TutorialOverlay.kt`**

```kotlin
package com.iris.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iris.audio.TtsManager

private val LINES = listOf(
    "Bem-vindo ao Iris. Iris é seu olho digital.",
    "A câmera fica nas costas do celular. Segure normalmente, com a tela voltada para o seu rosto.",
    "Para descrever o que está à sua frente, mantenha o celular vertical, com a parte de baixo apontando para o chão.",
    "Para ler um texto sobre uma mesa, deite o celular paralelo ao papel, com a tela voltada para cima.",
    "Existem três modos. O modo é falado em voz alta sempre que você troca.",
    "Para começar, toque duas vezes em qualquer lugar da tela.",
)

@Composable
fun TutorialOverlay(tts: TtsManager, onFinish: () -> Unit) {
    var lineIndex by remember { mutableStateOf(0) }

    LaunchedEffect(lineIndex) {
        if (lineIndex < LINES.size) {
            tts.speak(LINES[lineIndex])
            lineIndex++
        } else {
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable { onFinish() }
            .semantics { contentDescription = "Tutorial em andamento. Toque duas vezes para pular." }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Iris",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tutorial",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Toque para pular",
                color = Color(0xFFCCCCCC),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

- [ ] **Step 2: Add `markTutorialDone()` to AppViewModel**

In `app/src/main/java/com/iris/ui/AppViewModel.kt`, add inside the class:

```kotlin
fun markTutorialDone() {
    _state.update { it.copy(tutorialDone = true) }
}
```

- [ ] **Step 3: Show TutorialOverlay in MainActivity when phase is Idle and tutorial not done**

In `MainActivity.kt`, replace the `else -> MainScreen(viewModel = viewModel)` branch with:

```kotlin
else -> {
    if (!state.tutorialDone) {
        TutorialOverlay(tts = app.tts, onFinish = { viewModel.markTutorialDone() })
    } else {
        MainScreen(viewModel = viewModel)
    }
}
```

Add the import: `import com.iris.ui.screens.TutorialOverlay`.

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/screens/TutorialOverlay.kt app/src/main/java/com/iris/ui/AppViewModel.kt app/src/main/java/com/iris/MainActivity.kt
git commit -m "feat(ui): first-launch TutorialOverlay with TTS-narrated steps"
```

---

## Task 23: Persist tutorialDone via SharedPreferences

**Files:**
- Create: `app/src/main/java/com/iris/util/Prefs.kt`
- Modify: `app/src/main/java/com/iris/ui/AppViewModel.kt`

- [ ] **Step 1: Create `Prefs.kt`**

```kotlin
package com.iris.util

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("iris_prefs", Context.MODE_PRIVATE)

    var tutorialDone: Boolean
        get() = sp.getBoolean(KEY_TUTORIAL, false)
        set(value) { sp.edit().putBoolean(KEY_TUTORIAL, value).apply() }

    var preflightDone: Boolean
        get() = sp.getBoolean(KEY_PREFLIGHT, false)
        set(value) { sp.edit().putBoolean(KEY_PREFLIGHT, value).apply() }

    companion object {
        private const val KEY_TUTORIAL = "tutorialDone"
        private const val KEY_PREFLIGHT = "preflightDone"
    }
}
```

- [ ] **Step 2: Inject Prefs into AppViewModel**

Modify `AppViewModel.kt`:

1. Add `private val prefs: Prefs` to the constructor (place after `speech`).
2. In the `init {}` block, add at the top:
   ```kotlin
   _state.update { it.copy(tutorialDone = prefs.tutorialDone, preflightDone = prefs.preflightDone) }
   ```
3. Replace `markTutorialDone()` with:
   ```kotlin
   fun markTutorialDone() {
       prefs.tutorialDone = true
       _state.update { it.copy(tutorialDone = true) }
   }

   fun markPreflightDone() {
       prefs.preflightDone = true
       _state.update { it.copy(preflightDone = true) }
   }
   ```
4. Add `import com.iris.util.Prefs` at the top.

- [ ] **Step 3: Update `AppViewModelFactory.kt` to pass Prefs**

```kotlin
package com.iris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.iris.ai.GemmaManager
import com.iris.audio.SpeechManager
import com.iris.audio.TtsManager
import com.iris.camera.CameraManager
import com.iris.util.Prefs

class AppViewModelFactory(
    private val gemma: GemmaManager,
    private val tts: TtsManager,
    private val camera: CameraManager,
    private val speech: SpeechManager,
    private val prefs: Prefs,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        return AppViewModel(gemma, tts, camera, speech, prefs) as T
    }
}
```

- [ ] **Step 4: Update `MainActivity.kt` to construct Prefs**

Replace the `viewModels` delegate block with:

```kotlin
private val viewModel: AppViewModel by viewModels {
    val app = IrisApp.from(this)
    AppViewModelFactory(
        gemma = app.gemma,
        tts = app.tts,
        camera = CameraManager(this, this),
        speech = SpeechManager(this),
        prefs = com.iris.util.Prefs(this),
    )
}
```

- [ ] **Step 5: Update `AppViewModelTest.kt` to mock Prefs**

In `app/src/test/java/com/iris/ui/AppViewModelTest.kt`, modify the `newViewModel` helper:

```kotlin
private fun newViewModel(
    gemmaState: MutableStateFlow<GemmaManager.ModelState> =
        MutableStateFlow(GemmaManager.ModelState.Ready("E2B")),
): Pair<AppViewModel, MutableStateFlow<GemmaManager.ModelState>> {
    val gemma: GemmaManager = mockk(relaxed = true)
    val tts: TtsManager = mockk(relaxed = true)
    val camera: CameraManager = mockk(relaxed = true)
    val speech: SpeechManager = mockk(relaxed = true)
    val prefs: com.iris.util.Prefs = mockk(relaxed = true)
    every { gemma.state } returns gemmaState
    coEvery { gemma.load() } returns Unit
    every { tts.isSpeaking } returns MutableStateFlow(false)
    every { tts.isReady } returns MutableStateFlow(true)
    every { tts.ptBrAvailable } returns MutableStateFlow(true)
    every { prefs.tutorialDone } returns true
    every { prefs.preflightDone } returns true
    return AppViewModel(gemma, tts, camera, speech, prefs) to gemmaState
}
```

- [ ] **Step 6: Build and run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all tests pass.

- [ ] **Step 7: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/util/ app/src/main/java/com/iris/ui/AppViewModel.kt app/src/main/java/com/iris/ui/AppViewModelFactory.kt app/src/main/java/com/iris/MainActivity.kt app/src/test/java/com/iris/ui/AppViewModelTest.kt
git commit -m "feat: persist tutorialDone and preflightDone in SharedPreferences"
```

---

## Task 24: Pre-flight checks (TTS + STT offline packages)

**Files:**
- Modify: `app/src/main/java/com/iris/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/iris/MainActivity.kt`
- Create: `app/src/main/java/com/iris/ui/screens/PreflightScreen.kt`

- [ ] **Step 1: Create `PreflightScreen.kt`**

```kotlin
package com.iris.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PreflightScreen(
    ttsAvailable: Boolean,
    sttAvailable: Boolean,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.Top) {
            Text(
                text = "Configuração inicial",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (!ttsAvailable) {
                ActionRow(
                    title = "Voz em português offline",
                    desc = "Não está instalada. Sem ela, Iris não consegue falar com você.",
                    actionLabel = "Abrir configurações de voz",
                    semanticDesc = "Abrir configurações de síntese de voz para baixar pacote português offline.",
                    onClick = {
                        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!sttAvailable) {
                ActionRow(
                    title = "Reconhecimento de voz offline",
                    desc = "Não está instalado. Sem ele, o Modo Pergunta não funciona.",
                    actionLabel = "Abrir configurações de voz",
                    semanticDesc = "Abrir configurações de reconhecimento de voz para baixar pacote português offline.",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF333333))
                    .clickable(onClick = onSkip)
                    .padding(20.dp)
                    .semantics { contentDescription = "Continuar mesmo assim. Toque duas vezes." },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Continuar mesmo assim",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    desc: String,
    actionLabel: String,
    semanticDesc: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = desc, color = Color(0xFFCCCCCC), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFD600))
                .clickable(onClick = onClick)
                .padding(12.dp)
                .semantics { contentDescription = semanticDesc },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                color = Color.Black,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
```

- [ ] **Step 2: Add preflight detection in MainActivity**

In `MainActivity.kt`, add before the `when` branch matching `state.tutorialDone`:

```kotlin
val app = IrisApp.from(this)
val ttsAvailable by app.tts.ptBrAvailable.collectAsStateWithLifecycle()
val sttAvailable = remember {
    android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(this@MainActivity)
}
```

Then add a new branch in the `when {}` chain *before* `state.phase is AppPhase.LoadingModel`:

```kotlin
!state.preflightDone && (!ttsAvailable || !sttAvailable) ->
    PreflightScreen(
        ttsAvailable = ttsAvailable,
        sttAvailable = sttAvailable,
        onSkip = { viewModel.markPreflightDone() },
    )
```

Add import: `import com.iris.ui.screens.PreflightScreen`.

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/screens/PreflightScreen.kt app/src/main/java/com/iris/MainActivity.kt
git commit -m "feat: pre-flight check for offline TTS and STT packages"
```

---

## Task 25: Thermal throttling guard

**Files:**
- Modify: `app/src/main/java/com/iris/ui/AppViewModel.kt`

- [ ] **Step 1: Add thermal check to `trigger()`**

In `AppViewModel.kt`, add at the very start of `trigger()` (right after the function declaration):

```kotlin
fun trigger() {
    val pm = getApplication<android.app.Application>()
        .getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    if (android.os.Build.VERSION.SDK_INT >= 29 &&
        pm.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_CRITICAL) {
        viewModelScope.launch {
            tts.speak("O aparelho está aquecendo. Aguarde alguns segundos e tente de novo.")
        }
        return
    }
    // ... rest of trigger() implementation
}
```

This requires `AppViewModel` to extend `AndroidViewModel` instead of `ViewModel`. Update the class declaration:

```kotlin
class AppViewModel(
    application: android.app.Application,
    private val gemma: GemmaManager,
    private val tts: TtsManager,
    private val camera: CameraManager,
    private val speech: SpeechManager,
    private val prefs: Prefs,
) : androidx.lifecycle.AndroidViewModel(application) {
```

- [ ] **Step 2: Update `AppViewModelFactory.kt` to pass Application**

```kotlin
class AppViewModelFactory(
    private val application: android.app.Application,
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
```

- [ ] **Step 3: Update `MainActivity.kt` to pass `application`**

In the `viewModels { ... }` factory:

```kotlin
AppViewModelFactory(
    application = application,
    gemma = app.gemma,
    tts = app.tts,
    camera = CameraManager(this, this),
    speech = SpeechManager(this),
    prefs = com.iris.util.Prefs(this),
)
```

- [ ] **Step 4: Update `AppViewModelTest.kt`**

In `newViewModel` helper, add:

```kotlin
val application: android.app.Application = mockk(relaxed = true)
val pm: android.os.PowerManager = mockk(relaxed = true)
every { application.getSystemService(android.content.Context.POWER_SERVICE) } returns pm
every { pm.currentThermalStatus } returns 0
```

And update the constructor call:

```kotlin
return AppViewModel(application, gemma, tts, camera, speech, prefs) to gemmaState
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all tests pass.

- [ ] **Step 6: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/AppViewModel.kt app/src/main/java/com/iris/ui/AppViewModelFactory.kt app/src/main/java/com/iris/MainActivity.kt app/src/test/java/com/iris/ui/AppViewModelTest.kt
git commit -m "feat(ui): block trigger when device is thermally critical"
```

---

## Task 26: CameraManager binding from MainScreen

**Files:**
- Modify: `app/src/main/java/com/iris/ui/MainScreen.kt`

- [ ] **Step 1: Bind camera in `MainScreen` LaunchedEffect**

In `MainScreen.kt`, modify the `AndroidView` factory to capture the `PreviewView` and add a `LaunchedEffect` that binds it via the camera. We need access to the `CameraManager` — pass it from MainActivity via the ViewModel.

First, add a public reference in `AppViewModel`:

```kotlin
val cameraManager: CameraManager get() = camera
```

Then in `MainScreen.kt`, modify the body:

```kotlin
@Composable
fun MainScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    LaunchedEffect(previewView) {
        val pv = previewView ?: return@LaunchedEffect
        runCatching { viewModel.cameraManager.bind(pv) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.trigger() }
            .semantics { contentDescription = "Toque duas vezes para descrever a cena no modo atual." }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    keepScreenOn = true
                    previewView = this
                }
            }
        )
        // ... rest unchanged
    }
}
```

Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/ui/MainScreen.kt app/src/main/java/com/iris/ui/AppViewModel.kt
git commit -m "feat(camera): bind PreviewView in MainScreen via ViewModel"
```

---

## Task 27: Crash logger to file

**Files:**
- Create: `app/src/main/java/com/iris/util/CrashLog.kt`
- Modify: `app/src/main/java/com/iris/IrisApp.kt`

- [ ] **Step 1: Create `CrashLog.kt`**

```kotlin
package com.iris.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val baseDir = context.getExternalFilesDir(null) ?: return
        val out = File(baseDir, "iris_crash.txt")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        out.appendText("[$timestamp] thread=${thread.name}\n$sw\n\n")
    }
}
```

- [ ] **Step 2: Wire CrashLog into `IrisApp.onCreate`**

```kotlin
override fun onCreate() {
    super.onCreate()
    com.iris.util.CrashLog.install(this)
    gemma = GemmaManager(applicationContext)
    tts = TtsManager(applicationContext)
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Stop and tell user to commit**

```bash
git add app/src/main/java/com/iris/util/CrashLog.kt app/src/main/java/com/iris/IrisApp.kt
git commit -m "feat: persistent crash log in external files for adb pull debugging"
```

---

## Task 28: Instrumented smoke test

**Files:**
- Create: `app/src/androidTest/java/com/iris/MainActivityE2ETest.kt`

- [ ] **Step 1: Create the test**

```kotlin
package com.iris

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityE2ETest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpensAndShowsLoadingOrModelMissing() {
        composeTestRule.waitForIdle()
        // We expect either:
        // - LoadingScreen (model present in test device emulator's external files)
        // - ModelMissingScreen (no model sideloaded — most likely on emulator)
        val anyExpected = listOf(
            "Carregando modelo Iris. Aguarde de quinze a trinta segundos.",
            "Modelo não encontrado",
        )
        val matches = anyExpected.any { desc ->
            composeTestRule.onAllNodes(hasContentDescription(desc, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assert(matches) { "Expected Loading or ModelMissing screen, found neither" }
    }
}
```

- [ ] **Step 2: Run the instrumented test (requires connected device or emulator)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.iris.MainActivityE2ETest"`
Expected: PASS.

- [ ] **Step 3: Stop and tell user to commit**

```bash
git add app/src/androidTest/java/com/iris/MainActivityE2ETest.kt
git commit -m "test: instrumented smoke test for MainActivity entry screens"
```

---

## Task 29: Manual test plan document

**Files:**
- Create: `docs/manual-test-plan.md`

- [ ] **Step 1: Create `docs/manual-test-plan.md`**

```markdown
# Iris — Manual Test Plan

Run on **Galaxy S21** and **Galaxy A55** before submitting to DEV.to. Mark each item as PASS / FAIL with notes.

## Setup

- [ ] APK installs cleanly on Android 12+ via `adb install`
- [ ] Without any model file, app shows ModelMissingScreen with adb push command literal
- [ ] After `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/`, app loads E2B (banner shows "E2B")
- [ ] After `adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.iris/files/`, app prefers E4B (banner shows "E4B")
- [ ] Full uninstall + reinstall: tutorial plays again on first launch

## Inference (each mode)

- [ ] **Continuous, S21**: tap → "Analisando..." in <0.5s → first description word in <8s → full response in <12s
- [ ] **Continuous, A55**: tap → first word in <10s → full in <14s
- [ ] **Reading, S21**: aim at a printed page → reads text faithfully (no hallucinations)
- [ ] **Reading, no text**: aim at a wall → "Não vejo texto..." or similar
- [ ] **Question, S21**: tap → "Faça sua pergunta." → speak "tem uma porta?" → answer references door

## Quality check (skips inference)

- [ ] Cover lens with finger → "Imagem muito escura..."
- [ ] Aim at bright window → "Imagem muito clara..."
- [ ] Aim at blank wall → "Não vejo nada com detalhes..."
- [ ] Shake phone during capture → "Imagem desfocada..."

## Streaming TTS

- [ ] First sentence is spoken before model finishes generating (verifiable: total response time > spoken-first-word time + 2s)

## Errors

- [ ] Deny camera permission → FatalErrorScreen with retry button + TTS narrates
- [ ] Deny mic permission → Pergunta button still tappable but disabled / narrates "Modo pergunta indisponível..."
- [ ] Force-stop mid-inference → relaunch is clean
- [ ] Background app mid-inference → narration stops, camera released
- [ ] Re-tap mid-narration → previous narration cuts, new "Analisando..." starts

## Accessibility (TalkBack ON)

- [ ] Three mode buttons announce PT-BR descriptions correctly
- [ ] Double-tap on a mode button activates it
- [ ] PreviewView does not announce visual debris
- [ ] LoadingScreen narrates "Carregando modelo..."
- [ ] Mode selection (without trigger) narrates "Modo X selecionado." (V2 — long-press)
- [ ] Tutorial plays automatically on first launch and is skippable with one tap

## Performance budgets (S21)

- [ ] Cold start to Idle: <30s
- [ ] Tap → first TTS word: <8s (E2B)
- [ ] Tap → full response done: <14s (E2B)
- [ ] After 10 inferences: memory stable around 3GB, no leak
- [ ] After 10 inferences: device not THERMAL_STATUS_CRITICAL

## Performance budgets (A55)

- [ ] Cold start to Idle: <40s
- [ ] Tap → first TTS word: <10s (E2B)
- [ ] Tap → full response done: <16s (E2B)

## Pre-flight

- [ ] On a device WITHOUT pt-BR TTS pack, PreflightScreen offers to open TTS settings
- [ ] On a device WITHOUT pt-BR STT pack, PreflightScreen offers to open voice input settings
```

- [ ] **Step 2: Stop and tell user to commit**

```bash
git add docs/manual-test-plan.md
git commit -m "docs: manual test plan for S21 and A55"
```

---

## Task 30: README with setup steps

**Files:**
- Create: `README.md`

- [ ] **Step 1: Create `README.md`**

```markdown
# Iris

An offline Android visual assistant for blind and low-vision users. Built for the **DEV.to Gemma 4 Challenge** (deadline 2026-05-24). Runs Gemma 4 multimodal entirely on-device via MediaPipe LiteRT-LM — no cloud calls, no internet required.

## What it does

Point the back camera at something and tap. Iris narrates what it sees in Brazilian Portuguese. Three modes:

- **Contínuo** — describes the scene in front of you.
- **Pergunta** — ask a specific question by voice.
- **Leitura** — full OCR-narration of any visible text.

## Requirements

- Android 12+ (minSdk 31)
- 8GB RAM (Galaxy S21 / Galaxy A55 or better)
- Free space: ~3GB for the model
- Offline pt-BR TTS voice pack
- Offline pt-BR speech recognition pack

## First-time setup (sighted helper)

1. Install the APK:
   ```bash
   adb install -r iris.apk
   ```

2. Download the Gemma 4 E2B model from Hugging Face:
   - https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
   - Save the file as `gemma-4-E2B-it.litertlm`.

3. Push the model to the phone:
   ```bash
   adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/
   ```

4. (Optional, for higher quality) Push the E4B variant too. Iris will prefer E4B when both are present.

5. Open the app. The first launch:
   - Plays a TTS-narrated tutorial.
   - Pre-flights the offline TTS / STT packs (and opens the right Settings page if missing).

## Architecture

See [`docs/superpowers/specs/2026-05-09-iris-design.md`](docs/superpowers/specs/2026-05-09-iris-design.md).

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Test

```bash
./gradlew :app:testDebugUnitTest                     # unit tests
./gradlew :app:connectedDebugAndroidTest             # instrumented (needs device)
```

Manual test plan: [`docs/manual-test-plan.md`](docs/manual-test-plan.md).

## License

Apache 2.0
```

- [ ] **Step 2: Stop and tell user to commit**

```bash
git add README.md
git commit -m "docs: README with setup, build, and test instructions"
```

---

## Final integration verification

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — SystemPromptsTest (8), SentenceBufferTest (8), FrameQualityTest (5), AppViewModelTest (2). Total: 23 tests.

- [ ] **Step 2: Build release APK**

Run: `./gradlew :app:assembleDebug`
Expected: APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Install on real device and verify model-missing flow**

Run: `./gradlew :app:installDebug`
Open the app. Without sideloading the model, you should see the ModelMissingScreen and hear TTS narration.

- [ ] **Step 4: Sideload model and verify Idle flow**

Run:
```bash
adb push <path-to>/gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/
```
Reopen the app. Loading screen → tutorial → main screen with "E2B" banner.

- [ ] **Step 5: Run the manual test plan from `docs/manual-test-plan.md`**

Mark every item, fix what fails. This is the ship gate.

- [ ] **Step 6: Tell user the project is ready to publish**

Summarize: number of commits, manual-test-plan results, total LOC, what works, what's V2.
