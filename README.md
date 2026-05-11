# Iris

[🇧🇷 Leia em português](README-pt_br.md)

An offline Android visual assistant for blind and low-vision users. Built for the **DEV.to Gemma 4 Challenge** (deadline 2026-05-24). Runs Gemma 4 multimodal entirely on-device via LiteRT-LM — no cloud calls, no internet required.

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
   adb install -r app/build/outputs/apk/debug/iris-0.1.0-debug.apk
   ```

2. Download the Gemma 4 E2B model from Hugging Face:
   - https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
   - Save the file as `gemma-4-E2B-it.litertlm`.

3. Push the model to the phone:
   ```bash
   adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/
   ```

4. (Optional, as fallback) Push the E4B variant too. When both are present, Iris loads **E2B first** (faster, snappier UX on S21/A55) and falls back to E4B only if E2B fails to load.

5. Open the app. The first launch:
   - Plays a TTS-narrated tutorial.
   - Pre-flights the offline TTS / STT packs (and opens the right Settings page if missing).

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Test

```bash
./gradlew :app:testDebugUnitTest                     # 23 unit tests (run on JDK 21 toolchain)
./gradlew :app:connectedDebugAndroidTest             # instrumented (needs device)
```

> **Note:** unit tests run on a JDK 21 toolchain (auto-downloaded by Gradle the first time) because the `litertlm` library is compiled with Java 21 bytecode. The main app code still targets JDK 17.

Manual test plan: [`docs/manual-test-plan.md`](docs/manual-test-plan.md).

## License

Apache 2.0
