# Bert's Atlas

Bert's Atlas is an offline geography trivia game for Android and iOS. Product behavior lives in `spec.md`, and engineering guidance lives in `principles.md`.

## Architecture

- `domain`: pure Kotlin Multiplatform quiz, timing, scoring, mastery, streak, and selection rules
- `composeApp`: shared Compose UI, bundled resources, Room persistence, and platform adapters
- `catalogTool`: non-test JVM catalog validator and editorial report generator
- `androidApp`: thin Android application host
- `iosApp`: thin Xcode application host

The platform hosts use `app.bertsatlas`. V1 has no accounts, telemetry, advertising, runtime network dependency, or Android internet permission. Catalog content and progress stay on the device.

## Build and run

Install JDK 21, the Android SDK, Xcode, and `just`. The checked-in Justfile defaults to the local JDK and SDK paths used by this project; override `JAVA_HOME` or `ANDROID_HOME` when yours differ.

```sh
just --list
just build
```

Build the Android APK, or install and launch it on the configured device:

```sh
just android
just install-android
```

`install-android` targets OnePlus device `663c91b1` by default. Override it with `BERTS_ATLAS_DEVICE=<serial>`.

Build the shared simulator framework and Xcode host, or also install and launch the host:

```sh
just ios
just run-ios
```

The default simulator is `iPhone 17 Pro`. Override it with `BERTS_ATLAS_SIMULATOR_DEVICE="<simulator name or UDID>"`. Regenerate `iosApp/iosApp.xcodeproj` with `xcodegen generate --spec iosApp/project.yml` after changing the XcodeGen specification.

## Catalog gates

```sh
just catalog
just release
```

`just catalog` verifies that the bundled catalog is structurally safe to load and writes `catalogTool/build/reports/catalog-review.txt`. Structural success does not mean the content is editorially approved.

`just release` builds release artifacts without running automated tests and invokes the stricter editorial gate. It intentionally fails while any country, capital, fact, question, confusion explanation, or flag audit lacks documented human approval.

## Release prerequisites

Automated test suites are outside the V1 verification strategy; follow the manual acceptance matrix in `spec.md`. The currently available OnePlus covers Android 15/API 35, and the installed simulator covers iOS 26.1. Android API 26, an iOS 15-compatible runtime or device, and a connected physical iPhone are unavailable here and remain mandatory release prerequisites. Do not publish based only on the local build recipes.
