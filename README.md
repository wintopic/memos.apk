# Memos APK

Android APK packaging repository for [usememos/memos](https://github.com/usememos/memos).

This repo turns the original Go backend plus embedded web frontend into an Android app that runs fully on-device:

- The Memos server runs inside the app on `127.0.0.1:5230`.
- The UI is rendered through Android `WebView`.
- Data is stored in the app sandbox on the device.
- GitHub Actions can build an installable APK even if you do not have a local Android toolchain.

## Status

- Current packaging target: installable `debug APK`
- Local backend: embedded Go server
- Local storage: SQLite in app-private directory
- Supported bridges: file picker, microphone, geolocation

## Repository Layout

- [`android/`](android/README.md): Android Studio project
- [`mobile/memosmobile/`](mobile/memosmobile): Go mobile entrypoint used by `gomobile bind`
- [`scripts/android/`](scripts/android): local binding/build scripts
- [`docs/android-github-build.md`](docs/android-github-build.md): GitHub Actions APK build guide

## Build Locally

Prerequisites:

- Go 1.26+
- Node.js 24+
- Android Studio
- Android SDK + NDK
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`

Build the Go binding AAR:

### Windows

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android\build-android-bindings.ps1
```

### macOS / Linux

```bash
chmod +x ./scripts/android/build-android-bindings.sh
./scripts/android/build-android-bindings.sh
```

Then open [`android/`](android/) in Android Studio and run the `app` target.

## Build On GitHub

If your machine does not have Go, Java, Android SDK, or NDK installed, use GitHub Actions.

The workflow file is:

- [`.github/workflows/android-apk.yml`](.github/workflows/android-apk.yml)

It builds:

- `memos-android-debug.apk`

That APK is directly installable because GitHub Actions builds it with the default debug signing key.

Detailed steps:

- [`docs/android-github-build.md`](docs/android-github-build.md)

## How The Android App Works

1. GitHub Actions or local scripts build the web frontend into `server/router/frontend/dist`.
2. `gomobile bind` packages the Go runtime and server entrypoint as `memosmobile.aar`.
3. The Android app starts the local Go server on launch.
4. `WebView` loads the local URL and uses the same frontend as the original web app.

## Upstream

This repository is based on:

- [usememos/memos](https://github.com/usememos/memos)

If you want the original server project, official docs, or upstream issue tracker, use that repository.

## License

This repository keeps the upstream project license:

- [MIT](LICENSE)
