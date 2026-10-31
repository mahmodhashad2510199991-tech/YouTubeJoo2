# YouTube joo

Standalone Android browser for YouTube built on **Mozilla GeckoView** (no Chrome/Brave/Firefox needed).
Kotlin · Gradle Kotlin DSL · minSdk 26 (Android 8.0) · targetSdk 34 · English + Arabic (RTL).

## Build

### Option A – GitHub Actions (no PC needed)
1. Create a GitHub repository and push this folder's contents (unzipped) to it.
2. Open **Actions → "Build debug APK"** (it also runs on every push).
3. When it finishes, download the artifact **YouTubeJoo-debug-apk** and install the `.apk`.

### Option B – Android Studio
1. Install Android Studio (Koala or newer) with JDK 17.
2. **File → Open** this folder, let Gradle sync (needs internet to reach `google()`, `mavenCentral()` and `https://maven.mozilla.org/maven2/`).
3. **Build → Build APK(s)**. Output: `app/build/outputs/apk/debug/app-debug.apk`.

### Option C – command line
Install JDK 17, Android SDK (platform 34) and Gradle 8.7, then: `gradle :app:assembleDebug`.
(No Gradle wrapper JAR is included; run `gradle wrapper --gradle-version 8.7` once if you want one.)

### GeckoView version
`gradle.properties` has `geckoviewVersion=+` (newest release from Mozilla's Maven).
After the first successful build, pin the exact version shown in the Gradle log for reproducible builds.
Browse versions: https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/
The APK is large (~100 MB+) because it bundles the Gecko engine. Only arm64-v8a and armeabi-v7a are included (no x86 emulator images).

## Features
- Embedded GeckoView, YouTube mobile (`m.youtube.com`) homepage, URL/search bar (plain text searches YouTube), back/forward/refresh-stop/home
- Fullscreen video (rotation-aware, back button exits fullscreen), sound playback, rotation without reload
- History (500 entries) and bookmarks, stored locally only
- Persistent cookies/sessions (Gecko profile) and automatic session-state restore after restart or engine crash
- Load-error page, offline notice, auto-retry when the network returns
- Content filtering: Gecko built-in Enhanced Tracking Protection (ad/analytic/social trackers) + Safe Browsing, on/off switch, per-site whitelist (Settings or menu → "Turn filtering off for this site")
- Settings: filtering, whitelist, clear history, clear cache, clear cookies, cookie policy, homepage, web theme (dark/light/system), about, version
- HTTPS-only (plain `http://` is upgraded/blocked), no `allowBackup`, only INTERNET + ACCESS_NETWORK_STATE permissions, no analytics, no passwords stored by the app
- Opens `youtube.com` / `youtu.be` links from other apps

## Known limitations (read this)
- **This project was authored without being compiled or run** (the build sandbox had no network access, so dependencies could not be resolved and no APK was produced). Expect that a first build may need small fixes, most likely GeckoView API signature changes between versions (e.g. `NavigationDelegate.onLocationChange`).
- **Ads on YouTube are NOT reliably blocked.** Gecko's tracking protection blocks known trackers, not YouTube's first-party video ads. A real ad blocker would need a WebExtension (e.g. uBlock Origin) installed through GeckoView's `WebExtensionController`; that is not bundled here, and YouTube changes its ad delivery frequently.
- Blocking all cookies (or third-party cookies) can break Google sign-in.
- Single tab only; new windows open in the current tab.
- "Dark mode" sets the preferred colour scheme for web content; the app UI is always dark.
- Whitelist exceptions are applied when a page's location changes; the first load of a newly whitelisted site may need a reload (the menu action reloads automatically).
- Nothing was tested for video playback, fullscreen, persistence or filtering behaviour on a device.

## Licensing
See `THIRD_PARTY_LICENSES.md`.
