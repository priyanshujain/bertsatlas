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

tasks.register<JavaExec>("validateCatalog") {
    group = "verification"
    description = "Validates the bundled catalog structure and writes the editorial review report."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("validateCatalogForRelease") {
    group = "verification"
    description = "Rejects structural defects and catalog records without documented human approval."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("--release")
    workingDir = rootProject.projectDir
}
