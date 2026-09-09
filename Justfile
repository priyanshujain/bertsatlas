set shell := ["zsh", "-cu"]

java_home := env_var_or_default("JAVA_HOME", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
android_home := env_var_or_default("ANDROID_HOME", "/opt/homebrew/share/android-commandlinetools")
device := env_var_or_default("BERTS_ATLAS_DEVICE", "663c91b1")
simulator_device := env_var_or_default("BERTS_ATLAS_SIMULATOR_DEVICE", "iPhone 17 Pro")
simulator_destination := "platform=iOS Simulator,name=" + simulator_device
adb := android_home + "/platform-tools/adb"

export JAVA_HOME := java_home
export ANDROID_HOME := android_home
export ANDROID_SDK_ROOT := android_home

default: build

# Build both runnable apps and validate bundled content.
build: android ios catalog

# Build the Android APK only.
android:
    ./gradlew :androidApp:assembleDebug

# Build the shared iOS simulator framework and the Xcode host app.
ios:
    ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination '{{simulator_destination}}' -derivedDataPath build/ios-derived-data build

# Build, install, and launch the app on the selected iOS simulator.
run-ios: ios
    xcrun simctl boot '{{simulator_device}}' 2>/dev/null || true
    xcrun simctl bootstatus '{{simulator_device}}' -b
    xcrun simctl install '{{simulator_device}}' "build/ios-derived-data/Build/Products/Debug-iphonesimulator/Bert's Atlas.app"
    xcrun simctl launch '{{simulator_device}}' app.bertsatlas

# Install and launch the debug app on the requested OnePlus device.
install-android: android
    '{{adb}}' -s '{{device}}' get-state
    '{{adb}}' -s '{{device}}' install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
    '{{adb}}' -s '{{device}}' shell am force-stop app.bertsatlas
    '{{adb}}' -s '{{device}}' shell monkey -p app.bertsatlas -c android.intent.category.LAUNCHER 1

# Run the structural content gate; editorial release signoff remains separate.
catalog:
    ./gradlew :catalogTool:validateCatalog

# Build release artifacts without running automated tests.
release:
    ./gradlew :androidApp:assembleRelease :composeApp:linkReleaseFrameworkIosArm64 :catalogTool:validateCatalogForRelease
