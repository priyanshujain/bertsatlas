set shell := ["zsh", "-cu"]

java_home := env_var_or_default("JAVA_HOME", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")
android_home := env_var_or_default("ANDROID_HOME", "/opt/homebrew/share/android-commandlinetools")
device := env_var_or_default("BERTOPIA_DEVICE", "663c91b1")
simulator_device := env_var_or_default("BERTOPIA_SIMULATOR_DEVICE", "iPhone 17")
simulator_destination :="platform=iOS Simulator,name=" + simulator_device
adb := android_home + "/platform-tools/adb"
developer_dir := env_var_or_default("DEVELOPER_DIR", "/Applications/Xcode.app/Contents/Developer")
signing_identity := "Developer ID Application: Priyanshu Jain (TQV87WLXK3)"
notary_profile := "bertsatlas-notary"
dmg_path := "desktopApp/build/compose/binaries/main/dmg/Bertopia-1.0.0.dmg"

export JAVA_HOME := java_home
export ANDROID_HOME := android_home
export ANDROID_SDK_ROOT := android_home
export DEVELOPER_DIR := developer_dir

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
    xcrun simctl install '{{simulator_device}}' "build/ios-derived-data/Build/Products/Debug-iphonesimulator/Bertopia.app"
    xcrun simctl launch '{{simulator_device}}' app.bertopia

# Install and launch the debug app on the requested OnePlus device.
install-android: android
    '{{adb}}' -s '{{device}}' get-state
    '{{adb}}' -s '{{device}}' install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
    '{{adb}}' -s '{{device}}' shell am force-stop app.bertopia
    '{{adb}}' -s '{{device}}' shell monkey -p app.bertopia -c android.intent.category.LAUNCHER 1

# Build, install, and launch the debug app on the connected iPhone.
install-ios:
    #!/bin/zsh
    set -euo pipefail
    devices=$(mktemp)
    xcrun devicectl list devices -q -j "$devices" >/dev/null
    iphone=$(jq -r '[.result.devices[] | select(.hardwareProperties.platform == "iOS" and .hardwareProperties.reality == "physical" and .connectionProperties.pairingState == "paired")][0].hardwareProperties.udid // empty' "$devices")
    rm -f "$devices"
    [[ -n "$iphone" ]] || { print -u2 "error: no paired iPhone or iPad found"; exit 1; }
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination "platform=iOS,id=$iphone" -derivedDataPath build/ios-device -allowProvisioningUpdates DEVELOPMENT_TEAM=TQV87WLXK3 CODE_SIGN_STYLE=Automatic build
    xcrun devicectl device install app --device "$iphone" "build/ios-device/Build/Products/Debug-iphoneos/Bertopia.app"
    xcrun devicectl device process launch --device "$iphone" app.bertopia

# Run the macOS desktop app from source.
desktop:
    ./gradlew :cli:linkReleaseExecutableMacosArm64 :desktopApp:run

# Package, sign, notarize and staple the macOS DMG with the bert CLI inside.
dmg:
    rm -rf desktopApp/build/compose/binaries
    ./gradlew :cli:linkReleaseExecutableMacosArm64 :desktopApp:packageDmg -Pbertopia.sign=true
    codesign --force --timestamp --sign '{{signing_identity}}' "{{dmg_path}}"
    xcrun notarytool submit "{{dmg_path}}" --keychain-profile '{{notary_profile}}' --wait
    xcrun stapler staple "{{dmg_path}}"
    spctl -a -t open --context context:primary-signature -vv "{{dmg_path}}"

# Compile content/packs sources into the bundled .apkg packs.
packs:
    ./gradlew :catalogTool:buildPacks

# Build the store index.json and versioned official packs into build/store.
store:
    ./gradlew :catalogTool:buildStore

# Print every upload publish would make, without touching R2.
publish-dry-run: store
    while IFS=$'\t' read -r key file type cache; do print -r -- "wrangler r2 object put 'berts-atlas/$key' --file '$file' --content-type '$type' --cache-control '$cache' --remote"; done < build/store/publish-plan.tsv

# Upload the store to the berts-atlas R2 bucket; index.json goes last.
publish: store
    while IFS=$'\t' read -r key file type cache; do wrangler r2 object put "berts-atlas/$key" --file "$file" --content-type "$type" --cache-control "$cache" --remote; done < build/store/publish-plan.tsv

# Run the structural content gate; editorial release signoff remains separate.
catalog:
    ./gradlew :catalogTool:validateCatalog

# Build release artifacts without running automated tests.
release:
    ./gradlew :androidApp:assembleRelease :composeApp:linkReleaseFrameworkIosArm64 :catalogTool:validateCatalogForRelease
