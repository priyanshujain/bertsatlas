package app.bertopia.catalogtool

import app.bertopia.domain.pack.PackSourceBuilder
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream

private const val PACK_SOURCES = "content/packs"
private const val COVER_SVG = "cover.svg"
private const val COVER_PNG = "cover.png"

fun main() {
    val projectRoot = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .firstOrNull { it.resolve("settings.gradle.kts").exists() } ?: error("could not locate project root")
    projectRoot.resolve(PACK_SOURCES).listDirectoryEntries().sorted()
        .map { it.resolve(PackSourceBuilder.COVER_DIRECTORY) }
        .filter { it.resolve(COVER_SVG).exists() }
        .forEach { coverDirectory ->
            val png = coverDirectory.resolve(COVER_PNG)
            renderPng(coverDirectory.resolve(COVER_SVG), png)
            println("rendered $png")
        }
}

private fun renderPng(svg: Path, png: Path) {
    png.outputStream().use { output ->
        PNGTranscoder().transcode(TranscoderInput(svg.toUri().toString()), TranscoderOutput(output))
    }
}
