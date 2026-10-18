package app.bertopia.cli

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.PATH_MAX
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.isatty
import platform.posix.stderr
import platform.posix.stdout
import kotlin.system.exitProcess
import kotlin.time.Clock

@OptIn(ExperimentalForeignApi::class)
fun main(arguments: Array<String>) {
    val environment = Environment(
        homeDirectory = getenv("HOME")?.toKString() ?: "/",
        currentDirectory = memScoped { allocArray<ByteVar>(PATH_MAX).let { buffer -> getcwd(buffer, PATH_MAX.toULong())?.toKString() } } ?: ".",
        temporaryDirectory = getenv("TMPDIR")?.toKString() ?: "/tmp",
        libraryOverride = getenv("BERT_LIBRARY")?.toKString()?.takeIf { it.isNotBlank() },
        readStandardInput = ::readStandardInput,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )
    val result = Bert(environment).run(arguments.toList())
    if (result.stdout.isNotEmpty()) println(result.stdout)
    fflush(stdout)
    if (result.stderr.isNotEmpty()) fprintf(stderr, "%s\n", result.stderr)
    exitProcess(result.exitCode.code)
}

private fun readStandardInput(): String {
    if (isatty(0) == 1) throw CliFailure.usage("expected JSON on stdin or --file <path>")
    return generateSequence(::readlnOrNull).joinToString("\n")
}
