import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

val zstdSources = layout.projectDirectory.dir("src/nativeInterop/zstd")

data class ClangTarget(val sdk: String, val triple: String)

val zstdClangTargets = mapOf(
    "iosArm64" to ClangTarget("iphoneos", "arm64-apple-ios15.0"),
    "iosSimulatorArm64" to ClangTarget("iphonesimulator", "arm64-apple-ios15.0-simulator"),
    "macosArm64" to ClangTarget("macosx", "arm64-apple-macos13.0"),
)

val generateFixturePaths = tasks.register("generateFixturePaths") {
    val outputDir = layout.buildDirectory.dir("generated/fixturePaths")
    val fixturesDir = layout.projectDirectory.dir("src/commonTest/resources/fixtures").asFile.absolutePath
    inputs.property("fixturesDir", fixturesDir)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("app/bertsatlas/anki/FixturePaths.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package app.bertsatlas.anki\n\nconst val FIXTURES_DIRECTORY = \"$fixturesDir\"\n")
    }
}

kotlin {
    android {
        namespace = "app.bertsatlas.anki"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        val clangTarget = zstdClangTargets.getValue(target.name)
        val libraryDir = layout.buildDirectory.dir("zstd/${target.name}")
        val buildZstd = tasks.register<Exec>("buildZstd${target.name.replaceFirstChar { it.uppercase() }}") {
            inputs.dir(zstdSources)
            outputs.dir(libraryDir)
            val objectFile = libraryDir.get().file("zstd.o").asFile
            val archiveFile = libraryDir.get().file("libzstd.a").asFile
            doFirst { libraryDir.get().asFile.mkdirs() }
            commandLine(
                "sh", "-c",
                "xcrun --sdk ${clangTarget.sdk} clang -c -O3 -target ${clangTarget.triple} " +
                    "-o '$objectFile' '${zstdSources.file("zstd.c").asFile}' && " +
                    "xcrun --sdk ${clangTarget.sdk} libtool -static -o '$archiveFile' '$objectFile'",
            )
        }
        target.compilations.getByName("main").cinterops.create("zstd") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/zstd.def"))
            includeDirs(zstdSources)
            extraOpts("-libraryPath", libraryDir.get().asFile.absolutePath, "-staticLibrary", "libzstd.a")
            tasks.named(interopProcessingTaskName) { dependsOn(buildZstd) }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
            dependencies {
                compileOnly(libs.zstd.jni)
            }
        }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            api(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.protobuf)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
        }
        commonTest.get().kotlin.srcDir(generateFixturePaths)
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.zstd.jni)
        }
        androidMain.dependencies {
            implementation("${libs.zstd.jni.get()}@aar")
        }
    }
}
