import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":domain"))
    implementation(project(":anki"))
    implementation(project(":cardRender"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
}

val SIGNING_IDENTITY = "Priyanshu Jain (TQV87WLXK3)"
val signRelease = providers.gradleProperty("bertsatlas.sign").map { it.toBoolean() }.orElse(false).get()
val bundledCommandLineTool = rootProject.layout.projectDirectory.file("cli/build/bin/macosArm64/releaseExecutable/bert.kexe")
val desktopResources = layout.buildDirectory.dir("desktopResources")

val stageDesktopResources = tasks.register<Sync>("stageDesktopResources") {
    mustRunAfter(":cli:linkReleaseExecutableMacosArm64")
    from(bundledCommandLineTool) {
        into("macos-arm64/bin")
        rename { "bert" }
        filePermissions { unix("rwxr-xr-x") }
    }
    into(desktopResources)
}

val signBundledCommandLineTool = tasks.register<Exec>("signBundledCommandLineTool") {
    dependsOn(stageDesktopResources)
    val toolFile = desktopResources.get().file("macos-arm64/bin/bert").asFile
    val shouldSign = signRelease
    onlyIf { shouldSign && toolFile.exists() }
    commandLine(
        "codesign", "--force", "--options", "runtime", "--timestamp",
        "--identifier", "app.bertsatlas.bert",
        "--sign", "Developer ID Application: $SIGNING_IDENTITY",
        toolFile.absolutePath,
    )
}

compose.desktop {
    application {
        mainClass = "app.bertsatlas.desktop.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.JETBRAINS)
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("-Dapple.laf.useScreenMenuBar=true")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Bert's Atlas"
            packageVersion = "1.0.0"
            description = "Learn anything with Bert"
            vendor = "Bert's Atlas"
            modules("java.naming", "jdk.unsupported")
            appResourcesRootDir.set(desktopResources)

            macOS {
                bundleID = "app.bertsatlas"
                iconFile.set(project.file("icons/BertsAtlas.icns"))
                minimumSystemVersion = "13.0"
                dockName = "Bert\u2019s\u00A0Atlas"
                appCategory = "public.app-category.education"
                entitlementsFile.set(project.file("macos/entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("macos/entitlements.plist"))
                fileAssociation(
                    mimeType = "application/vnd.anki.apkg",
                    extension = "apkg",
                    description = "Anki Deck Package",
                )
                fileAssociation(
                    mimeType = "application/vnd.anki.colpkg",
                    extension = "colpkg",
                    description = "Anki Collection Package",
                )
                signing {
                    sign.set(signRelease)
                    identity.set(SIGNING_IDENTITY)
                }
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(stageDesktopResources, signBundledCommandLineTool) }

val distributableAppDirectory = layout.buildDirectory.dir("compose/binaries/main/app")
tasks.matching { it.name == "createDistributable" }.configureEach {
    val appDirectory = distributableAppDirectory
    doLast {
        appDirectory.get().asFile.walk()
            .filter { it.name == "bert" && it.parentFile.name == "bin" }
            .forEach { it.setExecutable(true, false) }
    }
}

tasks.withType<JavaExec>().configureEach {
    providers.gradleProperty("bertsatlas.home").orNull?.let { systemProperty("bertsatlas.home", it) }
    providers.gradleProperty("bertsatlas.openEditor").orNull?.let { systemProperty("bertsatlas.openEditor", it) }
    providers.gradleProperty("bertsatlas.cliInstallDirectory").orNull?.let { systemProperty("bertsatlas.cliInstallDirectory", it) }
    systemProperty("bertsatlas.debug", providers.gradleProperty("bertsatlas.debug").orElse("true").get())
}
