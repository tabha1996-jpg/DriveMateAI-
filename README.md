# DriveMate AI — private Android starter v0.1

This is a **starter**, not the complete AI travel agent. Includes a minimal UI, device authentication prompt, voice-to-text via Android's speech service, local keyword detection, text-to-speech, and Google Maps search handoff. Does **not** include OpenAI, live Places search, route sync, charging availability, encrypted chat database, or autonomous app control. Android speech service may process audio externally depending on device configuration.

## Build online (no Android Studio)
1. Create a **private** GitHub repository named `DriveMateAI`.
2. Upload the **contents** of this folder to the repository root, preserving `.github/workflows/build-apk.yml`.
3. Open repository → Actions → Build private APK → Run workflow (or push to main).
4. Download artifact `DriveMateAI-debug-apk` from the successful run; extract `app-debug.apk`.
5. On your Android phone, open the APK and approve installation from that source. Turn off that installation permission afterward.

The GitHub Actions runner uses a temporary debug signing key. APKs from separate builds may not upgrade in place; preserve a stable signing key before storing important app data. Never upload secrets/API keys into source control. No permissions for payments or accessibility are requested.

## Privacy / security notes
This version has no account, analytics SDK, server, payment integration, or local conversation database. It blocks normal screenshots. The biometric prompt is **not yet a complete security gate**: the UI is constructed before authentication; production version must gate all actions behind authentication and re-authenticate on resume. Device speech recognition can use a cloud speech provider. Google Maps handoff shares the query with Maps. No external AI model is connected.
