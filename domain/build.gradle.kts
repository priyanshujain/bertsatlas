import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

val generateTestResourcePaths = tasks.register("generateTestResourcePaths") {
    val outputDir = layout.buildDirectory.dir("generated/testResourcePaths")
    val resourcesDir = layout.projectDirectory.dir("src/commonTest/resources").asFile.absolutePath
    inputs.property("resourcesDir", resourcesDir)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("app/bertsatlas/domain/TestResourcePaths.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package app.bertsatlas.domain\n\nconst val TEST_RESOURCES_DIRECTORY = \"$resourcesDir\"\n")
    }
}

kotlin {
    android {
        namespace = "app.bertsatlas.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":anki"))
            implementation(libs.kotlinx.io.core)
        }
        commonTest.get().kotlin.srcDir(generateTestResourcePaths)
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
