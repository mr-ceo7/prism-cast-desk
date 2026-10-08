# 📡 Prism Cast

> **Secure, ultra-low latency wireless screen broadcasting with remote dashboards, motion alarms, and session recording.**

[![Release](https://img.shields.io/github/v/release/mr-ceo7/prism-cast-desk?style=flat-square&color=blue)](https://github.com/mr-ceo7/prism-cast-desk/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/mr-ceo7/prism-cast-desk/release-apk.yml?style=flat-square)](https://github.com/mr-ceo7/prism-cast-desk/actions)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Linux%20%7C%20Desktop-lightgrey?style=flat-square)](#)

---

## Features

- 📡 **Real-time screen broadcasting** over local HTTP/MJPEG
- 🖥️ **Desktop viewer** (Java Swing) for monitoring streams on PC/Linux
- 🔔 **Motion detection** with alarm logging
- 📼 **Session recording** with local database history
- 🔄 **Automatic updates** — the app checks GitHub Releases and installs new versions in the background

## Project Structure

| Module | Description |
|--------|-------------|
| `app/` | Android app (Kotlin + Jetpack Compose) — streams the device screen |
| `desktop/` | Desktop viewer (Java Swing) — receives and displays the stream |

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio), JDK 21+

### Android App

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` to your Gemini API key (see `.env.example` for reference)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

### Desktop Viewer

```bash
./gradlew :desktop:run
```

Or build a standalone JAR:

```bash
./gradlew :desktop:jar
java -jar desktop/build/libs/desktop-1.0.jar
```

## Auto-Update System

The Android app includes a built-in auto-update mechanism powered by GitHub Releases.

### How It Works

1. On every app launch **and** every 6 hours in the background, the app queries the [GitHub Releases API](https://api.github.com/repos/mr-ceo7/prism-cast-desk/releases/latest) for the latest release.
2. If a newer version is found (compared by `versionCode`), the APK asset is **downloaded silently** via Android's `DownloadManager`.
3. Once the download completes, the system package installer is invoked automatically to install the update.

> **Note:** Android requires a single user tap on "Install" for sideloaded APKs. The download itself is fully silent.

### Publishing a New Release

1. Bump `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts)
2. Commit and tag:
   ```bash
   git add -A && git commit -m "Release v1.0.1"
   git tag v1.0.1
   git push origin main --tags
   ```
3. The [GitHub Actions workflow](.github/workflows/release-apk.yml) will automatically:
   - Build the release APK
   - Create a GitHub Release with the APK attached
4. All existing app installations will pick up the update on their next check cycle.

### Release Tag Format

Tags must follow the `v<major>.<minor>.<patch>` format (e.g. `v1.0.0`, `v2.1.3`). The updater converts this to a numeric version code for comparison:

| Tag | Version Code |
|-----|-------------|
| `v1.0.0` | `10000` |
| `v1.0.1` | `10001` |
| `v1.2.0` | `10200` |
| `v2.0.0` | `20000` |

### GitHub Actions Secrets

The release workflow accepts the following optional secret in your GitHub repo settings:

| Secret | Description |
|--------|-------------|
| `GEMINI_API_KEY` | Your Gemini API key (used to build the `.env` during CI) |

## Building Release Packages

### Android APK

```bash
./gradlew :app:assembleRelease
```

The APK will be at `app/build/outputs/apk/release/`.

### Desktop (Linux .deb + Portable)

```bash
./build-release.sh
```

This creates both a `.deb` installer and a portable app-image under `release-builds/`.
