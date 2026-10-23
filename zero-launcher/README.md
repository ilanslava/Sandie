# ZERO Launcher

A lightweight, offline-first Android home screen built with Kotlin and native Android Views. This project lives in the Sandie repository without changing Sandie's existing app.

## Features
- Original graphite-and-lime visual identity
- Live local clock and date
- Search installed launcher apps by label or package
- Real app icons from Android
- Two-column app drawer
- Long-press an app to pin or unpin it in Quick Access
- Favorites stored locally on device
- Shortcuts to Android Settings and app settings
- No ads, analytics, network permission, or custom background service

## Compatibility
- Android 6.0+ (API 23+), including Samsung Galaxy A32
- Java 17, Kotlin, Android Gradle Plugin 8.7.3
- Debug APK build via GitHub Actions

## Build
Open the Actions tab in the Sandie repository and run **Build ZERO Launcher APK**. Download the `zero-launcher-debug` artifact, extract it, and install `app-debug.apk`.

The first build still needs to run in GitHub Actions to verify the build in a real Android build environment. After installation, press Home and select ZERO Launcher. To switch back, use Android Settings → Apps → Default apps → Home app (menu names can vary by One UI version).
