plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("app.bertsatlas.catalogtool.MainKt")
}

