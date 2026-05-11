# Iris — Manual Test Plan

Run on **Galaxy S21** and **Galaxy A55** before submitting to DEV.to. Mark each item PASS / FAIL with notes.

## Setup

- [ ] APK installs cleanly on Android 12+ via `adb install`
- [ ] Without any model file, app shows ModelMissingScreen with adb push command literal
- [ ] After `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/`, app loads E2B (banner shows "E2B")
- [ ] After `adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/com.iris/files/` (E2B still present), app keeps loading E2B (banner shows "E2B") — E2B is preferred per `GemmaConfig.MODEL_CANDIDATES`
- [ ] After removing E2B from the device (only E4B remains), app falls back to E4B (banner shows "E4B")
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
