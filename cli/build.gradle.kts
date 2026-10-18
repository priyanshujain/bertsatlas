plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    macosArm64 {
        binaries.executable {
            baseName = "bert"
            entryPoint = "app.bertsatlas.cli.main"
        }
    }

    sourceSets {
        macosArm64Main.dependencies {
            implementation(project(":domain"))
            implementation(project(":anki"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)
        }
        macosArm64Test.dependencies {
            implementation(kotlin("test"))
        }
    }
}
