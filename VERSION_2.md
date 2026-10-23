# DriveMate AI V2 — unified EV app-launch hub (guided build)

**What V2 adds:** one dashboard for HP eCharge, IndianOil e-Charge, BPCL eDrive, Tata Power EZ Charge, Jio-bp pulse Charge Pro, and Tata Motors iRA.ev. The screen detects supported installed apps using exact Android package queries (not QUERY_ALL_PACKAGES), asks before opening each provider, and lets the user choose **Open once**, **Remember approval**, or **Cancel**. Saved *launch* approvals can be revoked in one tap.

Natural Hinglish requests such as "Jio app kholo", "IndianOil app open" and "Tata Motors app access" reach the provider-specific permission prompt. Apps that are missing show a Play Store install handoff, subject to confirmation. Android text sharing can expose "Share with DriveMate" for explicitly shared station text; DriveMate displays it **without storage** and labels it unverified.

**V2 DOES NOT:** silently read data inside provider apps, authenticate into their accounts, retrieve live Gun 1/Gun 2 status, copy their payments, read a live Punch EV SOC, automatically rank same-side chargers, or perform phone-wide actions. Android permission to launch an app is **not** data API access. Individual gun-level integration requires documented, authorized operator endpoints and test credentials. An app may have payment buttons inside it; DriveMate cannot suppress the host app's payment UI. DriveMate never presses those controls.

### Existing V1 capabilities retained
- Device authentication, voice recognition with Hinglish hint, TTS
- Manual SOC and distance context; Google Maps driving navigation and basic charger/restaurant search
- Conservative messaging: third-party availability, gun status, same-side route detours and rating criteria **not verified**
- Experimental separate LiteRT-LM offline AI screen (requires the user to import an actual compatible model)

### Build and test
1. GitHub Actions should automatically run on PR updates. Confirm the latest commit is green before download.
2. Open successful workflow run -> Artifacts -> `DriveMateAI-debug-apk` -> download ZIP, extract `app-debug.apk`.
3. Install on an Android phone (existing debug builds might require uninstall because their signing keys differ).
4. Open V2, unlock with device credentials, tap **EV Apps • Request launch access (V2)**.
5. Each supported app displays Installed / Not installed; tap to receive the exact launch-consent dialog.
6. Choose Open once or Remember approval. Use **Revoke ALL saved launch approvals** to reset local approvals.
7. Say "Jio app kholo" in the speech field to reach the consent dialog without hunting among app icons.
8. Optionally share plain text via the provider's own Android share feature (if available), select **Share with DriveMate**, and confirm it is labelled unverified and never stored.

**Security:** This repository is public: never commit provider credentials, API keys, UPI IDs, banking information, OTPs or session cookies. No payment, broad app-listing or Accessibility permission is included. Speech recognition services may transmit audio under Android speech service policies. Search/navigation requests go to Google Maps. Store source credentials server-side if commercial API access is obtained.

**Delivery status:** A passing APK build confirms compilation only; it is not on-device acceptance testing or operator data access.

See `CHARGING_INTEGRATIONS.md` for connector-level data contracts.
