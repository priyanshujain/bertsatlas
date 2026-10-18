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
    implementation(libs.batik.transcoder)
    runtimeOnly(libs.batik.codec)
    implementation(project(":domain"))
    implementation(project(":anki"))
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

val renderCovers = tasks.register<JavaExec>("renderCovers") {
    group = "build"
    description = "Renders content/packs/<id>/cover/cover.svg to cover.png."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bertsatlas.catalogtool.CoverRendererKt")
    jvmArgs("-Djava.awt.headless=true")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("buildPacks") {
    group = "build"
    description = "Compiles content/packs sources into bundled .apkg packs."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bertsatlas.catalogtool.PackBuilderKt")
    dependsOn(renderCovers)
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("buildStore") {
    group = "build"
    description = "Builds the store index.json and versioned official packs into build/store."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.bertsatlas.catalogtool.StoreBuilderKt")
    dependsOn(renderCovers)
    workingDir = rootProject.projectDir
    providers.gradleProperty("storeBaseUrl").orNull?.let { args("--base-url", it) }
}
