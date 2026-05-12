# Iris

<p align="center">
  <img src="docs/assets/iris-logo-512.png" alt="Logo da Iris" width="128">
</p>

Um assistente visual Android **offline** para pessoas cegas e com baixa visão. Construído para o **DEV.to Gemma 4 Challenge** (prazo 2026-05-24). Roda o Gemma 4 multimodal inteiramente no dispositivo via LiteRT-LM — sem chamadas à nuvem, sem internet necessária.

> **Leia o artigo completo no DEV.to:** [Iris: an offline visual assistant in Brazilian Portuguese powered by Gemma 4](https://dev.to/juniormartinxo/iris-an-offline-visual-assistant-in-brazilian-portuguese-powered-by-gemma-4-2652)

## O que faz

Aponte a câmera traseira para algo e toque na tela. A Iris narra o que vê em português do Brasil. Três modos:

- **Contínuo** — descreve a cena à sua frente.
- **Pergunta** — você faz uma pergunta específica por voz.
- **Leitura** — narra todo o texto visível na imagem (OCR falado).

## Requisitos

- Android 12+ (minSdk 31)
- 8GB de RAM (Galaxy S21 / Galaxy A55 ou superior)
- Espaço livre: ~3GB para o modelo
- Pacote de voz TTS offline em pt-BR
- Pacote de reconhecimento de voz offline em pt-BR

## Configuração inicial (com ajuda de alguém vidente)

1. Instale o APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/iris-0.1.0-debug.apk
   ```

2. Baixe o modelo Gemma 4 E2B do Hugging Face:
   - https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
   - Salve o arquivo como `gemma-4-E2B-it.litertlm`.

3. Envie o modelo para o celular:
   ```bash
   adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/
   ```

4. (Opcional, como fallback) Envie também a variante E4B. Quando os dois estiverem presentes, a Iris carrega **o E2B primeiro** (mais rápido, melhor experiência no S21/A55) e cai para o E4B somente se o E2B falhar ao carregar.

5. Abra o app. Na primeira execução:
   - Toca um tutorial narrado por TTS.
   - Verifica os pacotes offline de TTS / STT (e abre a tela de Configurações correta se algum estiver faltando).

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Testes

```bash
./gradlew :app:testDebugUnitTest                     # testes unitários (rodam na toolchain JDK 21)
./gradlew :app:connectedDebugAndroidTest             # instrumentados (precisam de device)
```

> **Nota:** os testes unitários rodam numa toolchain JDK 21 (baixada automaticamente pelo Gradle na primeira execução) porque a biblioteca `litertlm` é compilada com bytecode Java 21. O código principal do app continua tendo JDK 17 como alvo.

Plano de testes manuais: [`docs/manual-test-plan.md`](docs/manual-test-plan.md).

## Licença

Apache 2.0
