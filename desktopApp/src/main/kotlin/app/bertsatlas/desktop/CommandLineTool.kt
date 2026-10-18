package app.bertsatlas.desktop

import java.io.File
import java.nio.file.Files

sealed interface CommandLineToolInstall {
    data class Installed(val linkPath: String) : CommandLineToolInstall
    data object NotBundled : CommandLineToolInstall
    data object Cancelled : CommandLineToolInstall
    data class Failed(val reason: String) : CommandLineToolInstall
}

class CommandLineTool(private val bundledTool: File?, private val targetDirectory: File) {
    val linkFile: File get() = File(targetDirectory, TOOL_NAME)

    fun install(): CommandLineToolInstall {
        val tool = bundledTool?.takeIf { it.canExecute() } ?: return CommandLineToolInstall.NotBundled
        return if (canWriteDirectly()) linkDirectly(tool) else linkWithAdministratorPrompt(tool)
    }

    private fun canWriteDirectly(): Boolean =
        if (targetDirectory.exists()) Files.isWritable(targetDirectory.toPath()) else targetDirectory.mkdirs()

    private fun linkDirectly(tool: File): CommandLineToolInstall = runCatching {
        Files.deleteIfExists(linkFile.toPath())
        Files.createSymbolicLink(linkFile.toPath(), tool.toPath())
        CommandLineToolInstall.Installed(linkFile.absolutePath)
    }.getOrElse { CommandLineToolInstall.Failed(it.message ?: "Could not create the link") }

    private fun linkWithAdministratorPrompt(tool: File): CommandLineToolInstall {
        val command = "mkdir -p ${shellQuoted(targetDirectory.absolutePath)} && ln -sf ${shellQuoted(tool.absolutePath)} ${shellQuoted(linkFile.absolutePath)}"
        val script = "do shell script ${appleScriptQuoted(command)} with administrator privileges"
        val process = ProcessBuilder("/usr/bin/osascript", "-e", script).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return when {
            process.waitFor() == 0 -> CommandLineToolInstall.Installed(linkFile.absolutePath)
            "-128" in output -> CommandLineToolInstall.Cancelled
            else -> CommandLineToolInstall.Failed(output.trim())
        }
    }

    companion object {
        const val TOOL_NAME = "bert"
        private const val DEFAULT_TARGET = "/usr/local/bin"

        fun forThisApp(): CommandLineTool {
            val resources = System.getProperty("compose.application.resources.dir")?.let(::File)
            val bundled = resources?.let { File(it, "bin/$TOOL_NAME") }
            val target = File(System.getProperty("bertsatlas.cliInstallDirectory") ?: DEFAULT_TARGET)
            return CommandLineTool(bundled, target)
        }

        fun shellQuoted(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        fun appleScriptQuoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
