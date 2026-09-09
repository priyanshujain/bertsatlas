# Bert's Atlas

Bert's Atlas is an offline geography trivia game for Android and iOS. Product behavior and acceptance criteria live in `spec.md`; engineering principles live in `principles.md`.

## Project layout

- `domain`: pure multiplatform quiz and progression rules
- `composeApp`: shared Compose UI and platform-neutral application code
- `androidApp`: thin Android launcher required by Android Gradle Plugin 9
- `catalogTool`: JVM catalog validator and human-review report generator
- `iosApp`: thin Xcode host for the shared Compose framework

The extra Android launcher is deliberate. AGP 9 does not support combining `com.android.application` and Kotlin Multiplatform in one module, so the three substantive modules remain separate from both thin platform hosts.

## Toolchain

Builds use JDK 21 and the checked-in Gradle wrapper. Compose Multiplatform 1.12 requires Android compile SDK 37; target SDK remains 36 and minimum SDK is 26.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :catalogTool:run
```

Generate the Xcode project after changing `iosApp/project.yml`:

```sh
cd iosApp
xcodegen generate
```

No automated test sources or test dependencies are part of V1. Verification is manual and release catalog validation is performed by `catalogTool`.
