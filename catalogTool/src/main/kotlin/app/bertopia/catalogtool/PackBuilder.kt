package app.bertopia.catalogtool

import app.bertopia.domain.pack.PackBuildException
import app.bertopia.domain.pack.PackSourceBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.system.exitProcess

private const val PACK_SOURCES = "content/packs"
private const val BUNDLED_PACKS = "composeApp/src/commonMain/composeResources/files/packs"
private const val WORKING_DIRECTORY = "catalogTool/build/packs"

fun main() {
    val projectRoot = findPackProjectRoot(Path.of("").toAbsolutePath())
    val outputDirectory = projectRoot.resolve(BUNDLED_PACKS).createDirectories()
    val packDirectories = projectRoot.resolve(PACK_SOURCES).listDirectoryEntries().filter { it.resolve(PackSourceBuilder.MANIFEST_FILE).exists() }.sorted()
        .filter { PackSourceBuilder.read(it.toString()).bundled }
    val failures = packDirectories.mapNotNull { packDirectory ->
        val output = outputDirectory.resolve("${packDirectory.name}.apkg")
        val working = projectRoot.resolve(WORKING_DIRECTORY).resolve(packDirectory.name)
        runCatching { buildPack(packDirectory, working, output) }.exceptionOrNull()?.let { packDirectory.name to it }
    }
    failures.forEach { (pack, failure) ->
        System.err.println("$pack: ${failure.message}")
        (failure as? PackBuildException)?.issues?.forEach { System.err.println("$pack: ${it.severity} ${it.code} ${it.noteGuid.orEmpty()} ${it.message}") }
    }
    if (failures.isNotEmpty()) exitProcess(1)
}

private fun buildPack(packDirectory: Path, workingDirectory: Path, output: Path) {
    val built = PackSourceBuilder.build(packDirectory.toString(), workingDirectory.toString(), output.toString())
    built.issues.forEach { println("${packDirectory.name}: ${it.severity} ${it.code} ${it.noteGuid.orEmpty()} ${it.message}") }
    println("${packDirectory.name}: ${built.notes} notes, ${built.mediaFiles} media, ${built.sizeBytes} bytes -> $output")
}

private fun findPackProjectRoot(start: Path): Path =
    generateSequence(start.normalize()) { it.parent }.firstOrNull { it.resolve("settings.gradle.kts").exists() }
        ?: error("could not locate project root from $start")
