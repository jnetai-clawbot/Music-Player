# JNet Music Player

A simple, beautiful music player for Android built with Kotlin and Material Design 3.

## Features

- 🎵 Scans and plays local music from your device
- 📂 Browse by Songs, Artists, Albums, and Playlists
- 🎨 Dark theme with Material Design 3
- 🔔 Notification controls for background playback
- 🔀 Shuffle and repeat modes
- 🔍 Search songs, artists, and albums
- 📋 Create and manage playlists
- ℹ️ About section with version info and update checker

## Technical Details

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0+)
- **Target SDK:** 34
- **Architecture:** MVVM with Repository pattern
- **Database:** Room for playlist storage
- **Playback:** MediaPlayer with foreground service
- **UI:** Material Design 3 (dark theme)

## Building

```bash
cd android
./gradlew assembleRelease
```

The release APK will be in `android/app/build/outputs/apk/release/`.

## GitHub Releases

APKs are automatically built and published via GitHub Actions on push to main.

## License

MIT