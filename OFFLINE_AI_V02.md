# DriveMateAI v0.2 offline AI experiment

Branch: `feature/offline-ai-v0.2`. Do not merge until the GitHub Actions build and an actual Vivo X300 device test succeed.

## Changes
- Adds Google LiteRT-LM Kotlin Android runtime (version resolved via `latest.release`; pin after successful CI).
- Adds `OfflineAiEngine.kt` with CPU-backed conversation and history maintained during the app session.
- Adds an Android file picker to import a compatible `.litertlm` model into app-private storage.
- Keeps biometric unlock, voice recognizer, TextToSpeech and Google Maps launch. Controls stay locked before authentication.
- Does not include a model binary or download one automatically.

## Model
1. Download a **LiteRT-LM-compatible** Gemma 3 1B IT `.litertlm` file from the official community collection: https://huggingface.co/litert-community
2. Check model card, license, exact runtime compatibility and available storage before downloading. A GGUF, ordinary TFLite or raw Hugging Face weights file will not work.
3. Transfer the `.litertlm` file to your phone using Files or USB. Do not commit it to GitHub.
4. Install branch APK, unlock, tap **Import .litertlm model**, and choose the file.
5. Wait for the CPU initialization message. Ask a text question. First load can be slow; model compatibility and device performance remain unverified.

## Build
GitHub > Actions > Build private APK > Run workflow > select `feature/offline-ai-v0.2` branch. Check the logs. Download artifact only if the build is green. The existing GitHub workflow is manual dispatch only.

## Caveats
- This is a prototype, **not yet verified by CI or a physical-device test**.
- Offline speech recognition depends on installed Android speech packs and provider; the `EXTRA_PREFER_OFFLINE` flag is only a preference, not a privacy guarantee. Text chat is offline after model import.
- No live EV charger data, traffic, installed-app automation or external AI APIs.
- Current model is loaded on CPU first; speed may be slow. Device NPU support is not assumed.
- Model file is stored privately in the app and may use substantial storage.
- Model responses can be inaccurate; do not rely on it for safety-critical driving decisions.
- Source: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
