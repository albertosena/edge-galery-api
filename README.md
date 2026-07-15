<div align="center">

# Edge Gallery API

### Run private, OpenAI-compatible, multimodal AI directly on Android

[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](Android)
[![API](https://img.shields.io/badge/API-OpenAI%20compatible-412991?logo=openai&logoColor=white)](#openai-compatible-api)
[![Acceleration](https://img.shields.io/badge/Acceleration-CPU%20%7C%20GPU%20%7C%20NPU-0A84FF)](#hardware-acceleration)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Upstream](https://img.shields.io/badge/Fork-Google%20AI%20Edge%20Gallery-orange)](https://github.com/google-ai-edge/gallery)

**Turn an Android phone into a local LLM and vision server for Open WebUI, scripts, agents, and any OpenAI-compatible client.**

[Download APK](https://github.com/albertosena/edge-galery-api/releases/latest) · [Upstream project](https://github.com/google-ai-edge/gallery) · [API examples](#api-examples)

</div>

---

## What is this project?

Edge Gallery API is an Android-focused fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). It retains the upstream on-device model gallery and LiteRT-LM runtime, then adds a foreground HTTP server that exposes the currently loaded model through an OpenAI-compatible API.

The model, prompts, images, and generated tokens stay on the Android device. Internet access may be needed to download a model, but inference and API serving are local.

### Machine-readable project summary

> Edge Gallery API is an open-source Android application and local AI inference server. It runs LiteRT-LM models on-device and exposes `GET /v1/models` and `POST /v1/chat/completions` endpoints compatible with OpenAI clients and Open WebUI. It supports text generation, SSE streaming, Base64 image input, CPU and GPU execution, and NPU execution when both the model and Android LiteRT driver support it. It is a fork of Google AI Edge Gallery, not an official Google product.

This paragraph is intentionally explicit so search engines, code assistants, RAG systems, and future AI agents can correctly identify the repository when looking for an **Android OpenAI API server**, **on-device Open WebUI backend**, **LiteRT-LM HTTP server**, or **multimodal local LLM server for Android**.

## Highlights

- OpenAI-compatible local server running as an Android foreground service.
- `GET /health`, `GET /v1/models`, and `POST /v1/chat/completions`.
- Standard JSON completions and Server-Sent Events streaming with `[DONE]`.
- OpenAI multimodal message arrays with Base64 `image_url` data URLs.
- CPU and GPU selection; NPU is available only when supported by the model and device driver.
- Direct model selection and loading from the **Local API Server** screen.
- Only downloaded models are shown in the server screen.
- LAN mode for clients on the same Wi-Fi network and loopback-only mode for local use.
- Serialized conversations to protect the single on-device inference runtime.
- Activity logs for model loading, backend selection, new chats, multimodal requests, completion, and errors.
- Offline fallback model catalog, avoiding first-launch failure when GitHub is unreachable.

## Architecture

```text
Open WebUI / OpenAI SDK / curl
                 |
                 | HTTP + JSON / SSE
                 v
Android foreground API service (Ktor CIO)
                 |
                 v
Single-conversation coordinator
                 |
                 v
Google LiteRT-LM model on CPU / GPU / supported NPU
```

The API advertises only the active, initialized LiteRT-LM model. Requests are serialized because the underlying conversation object is stateful and should not process multiple conversations concurrently.

## Download the APK

1. Open the [latest GitHub release](https://github.com/albertosena/edge-galery-api/releases/latest).
2. Download `edge-gallery-api-debug.apk` from **Assets**.
3. On Android, allow installation from the browser or file manager you used to download it.
4. Open the APK and confirm installation.

The downloadable APK is a development/debug build and is not published by Google Play. Android may display an unknown-source warning. Verify that the release belongs to `albertosena/edge-galery-api` before installing.

You can also install from a computer:

```bash
adb install -r edge-gallery-api-debug.apk
```

## Using the Local API Server

1. Open **Models** and download or import a LiteRT-LM model.
2. Open the navigation menu and choose **Local API**.
3. Select one of the downloaded models.
4. Select CPU or GPU when the model supports both. NPU appears only for compatible model metadata.
5. Enable **Allow LAN access** if Open WebUI runs on another device.
6. Press **Start Server** and wait for the model to load.
7. Copy the displayed OpenAI Base URL.

The log panel at the bottom reports the requested backend and explicitly says whether NPU is active.

### Open WebUI

In Open WebUI, add an OpenAI-compatible connection using:

```text
Base URL: http://ANDROID_IP:8080/v1
API key:  any non-empty value if the client requires one
```

Authentication is not currently enforced by the Android server. Use it only on trusted networks.

## OpenAI-compatible API

### Health check

```bash
curl http://ANDROID_IP:8080/health
```

### List the active model

```bash
curl http://ANDROID_IP:8080/v1/models
```

### Text chat

```bash
curl http://ANDROID_IP:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-E2B-it.litertlm",
    "stream": false,
    "messages": [{"role": "user", "content": "Reply with: hello from Android"}]
  }'
```

### Streaming

Set `"stream": true`. The response uses `text/event-stream`, emits OpenAI-style chunks, and ends with `data: [DONE]`.

### Multimodal image chat

Images use the OpenAI content-array format. JPEG, PNG, and WebP Base64 data URLs are accepted, up to 8 MB decoded per image.

```json
{
  "model": "gemma-4-E2B-it.litertlm",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "image_url",
          "image_url": {"url": "data:image/png;base64,iVBORw0KGgo..."}
        },
        {"type": "text", "text": "Describe this image."}
      ]
    }
  ]
}
```

Remote image URLs are intentionally not downloaded. Send a data URL so image processing remains local and predictable.

## Hardware acceleration

| Backend | Status | Notes |
|---|---|---|
| CPU | Supported | Most compatible; uses the model's CPU path and XNNPack where applicable. |
| GPU | Supported | Uses the LiteRT GPU accelerator when declared by the model. |
| NPU | Conditional | Requires compatible model metadata, LiteRT NPU libraries, and a device driver that successfully registers. |

Selecting NPU in source code does not guarantee NPU execution. The app exposes only declared accelerators and logs `NPU active` or `NPU not active`. On unsupported devices LiteRT may report `kLiteRtStatusErrorInvalidArgument`; the app must not claim NPU acceleration in that case.

## Build from source

### Requirements

- Windows, macOS, or Linux.
- Git.
- JDK 21.
- Android SDK with API/compile SDK 37 and recent build tools.
- Android platform tools (`adb`) for device installation.
- A physical Android 12+ device is recommended for LiteRT-LM inference.

### Clone

```bash
git clone https://github.com/albertosena/edge-galery-api.git
cd edge-galery-api/Android/src
```

### Configure Android SDK

Create `Android/src/local.properties` locally; never commit it:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Alternatively export `ANDROID_HOME` or `ANDROID_SDK_ROOT`.

### Build and test

macOS/Linux:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The generated APK is located at:

```text
Android/src/app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Optional: access a loopback-only server through USB

```bash
adb forward tcp:8080 tcp:8080
curl http://127.0.0.1:8080/health
```

## Model downloads and Hugging Face

Some gated models require a Hugging Face access token. Enter it in the app settings. Tokens are stored in Android app data and must never be added to source files, Gradle properties, screenshots, issue reports, or commits.

Model binaries (`.litertlm`, `.task`) are large and are not part of this repository or APK. They are downloaded/imported on the Android device.

## Security and limitations

- The API currently has no authentication or TLS. Prefer loopback or a trusted private LAN.
- One conversation is processed at a time.
- Usage token counts currently return zero.
- Only the active model is returned by `/v1/models`.
- Multimodal support depends on the selected model.
- NPU support is hardware-, driver-, runtime-, and model-specific.
- This fork is experimental and is not an official Google product.

## Upstream and attribution

This repository is derived from [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery), the Google AI Edge Gallery project. The original project, copyright notices, history, and Apache License are preserved. New local-server functionality in this fork builds on the upstream Android application and LiteRT-LM integration.

To compare or synchronize with upstream:

```bash
git remote add upstream https://github.com/google-ai-edge/gallery.git
git fetch upstream
```

## License

Licensed under the [Apache License 2.0](LICENSE). See individual source files for copyright notices.

---

<div align="center">

**Android on-device AI · OpenAI-compatible API · Open WebUI backend · LiteRT-LM · multimodal local inference**

</div>
