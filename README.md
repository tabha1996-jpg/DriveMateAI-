# DriveMate AI — Android driving companion (prototype V1)

This repository contains a **guided prototype**, not an integrated autonomous driving agent.

## V1 driving mode

The launcher opens DrivingActivity. After Android biometric/device authentication, it supports:
- Hinglish voice input through the installed Android speech-recognition service (may use cloud recognition).
- Manual battery state-of-charge %, a destination and requested search radius, stored locally.
- Simple extraction of a percentage or km radius from a spoken command.
- Google Maps driving directions, DC/CCS2 charger search, and restaurant search.
- A separate button to open the experimental offline-AI screen from the existing v0.2 code.
- Text-to-speech replies and explicit *unverified* warnings instead of fake live availability.

**Critical limitations:** Search in Google Maps is not constrained to the road ahead, the same carriageway, the entered 20-km radius or restaurant-review thresholds. It does not verify charger gun occupancy, functioning status, last session, price, review count, highway detour, or restaurant opening hours. Never treat a Maps result as a confirmed available charger. The manual SOC is not read from Tata iRA.ev. Do not operate this app while driving; have a passenger use it or pull over.

For a production agent, add a real routing provider that calculates legal driving access and detour, EV provider(s) that supply connector-level freshness/status, a Places provider for restaurants with ratings and counts, and explicit unknown/unverified states. Do not claim such integrations are included yet.

No payment/banking permissions or AccessibilityService. Do not commit API keys or tokens. The app is not a payment client.

## Build via GitHub Actions

1. Open **Actions → Build DriveMate AI APK → Run workflow** and select the V1 branch, or look at the PR build.
2. Once the workflow **succeeds**, download the artifact `DriveMateAI-debug-apk` and extract `app-debug.apk`.
3. Install on an Android phone. Debug signing keys can differ between workflow runs; uninstalling an older debug build may be necessary and clears its app data.

The current offline AI feature depends on the experimental LiteRT-LM dependency in `app/build.gradle.kts`, and requires a separately imported compatible `.litertlm` model. The driving mode does **not** depend on importing that model.

## Privacy

Trip fields are stored locally in app private preferences. Voice recognition uses the Android speech-recognition provider installed on the device, whose own privacy policy applies. Search and navigation handoffs share the query with Google Maps. Screenshots are restricted. The app does not read messages, payments or private data from other apps. Biometric/device lock gates both app modes.
