# Android Local App

This repository now includes an Android host app under [`/android`](.).

## Architecture

- The existing Go backend still serves the Memos API and embedded frontend.
- Android packages that backend as a local AAR built from [`/mobile/memosmobile`](../mobile/memosmobile).
- The native app starts the local HTTP server on `127.0.0.1:5230` and renders Memos through `WebView`.
- App data is stored in the app-private directory (`files/memos`), so notes stay local to the Android app sandbox.

## Prerequisites

- Go 1.26 or newer.
- Node.js 24 or newer.
- Android Studio with Android SDK and NDK installed.
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` set in your shell.

## Build The Go Bindings

From the repository root:

### Windows

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android\build-android-bindings.ps1
```

### macOS / Linux

```bash
chmod +x ./scripts/android/build-android-bindings.sh
./scripts/android/build-android-bindings.sh
```

That script does three things:

1. Builds the frontend into `server/router/frontend/dist`.
2. Installs and initializes `gomobile`.
3. Generates `android/app/libs/memosmobile.aar`.

## Run The App

1. Generate `android/app/libs/memosmobile.aar` with the script above.
2. Open [`/android`](.) in Android Studio.
3. Let Gradle sync.
4. Run the `app` configuration on a device or emulator.

The first screen should wait for `http://127.0.0.1:5230/healthz`, then load the local Memos instance.

## Current Scope

- Supported permissions: file picker, microphone recording, geolocation.
- Cleartext traffic is enabled only so the app can talk to its own localhost server.
- The Android host assumes the Go bindings AAR has already been generated.

## Build On GitHub

If you do not have a local Android environment, use the GitHub Actions workflow documented in [`docs/android-github-build.md`](../docs/android-github-build.md).
