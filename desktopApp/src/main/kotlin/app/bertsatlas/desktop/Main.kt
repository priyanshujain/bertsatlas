package app.bertsatlas.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.bertsatlas.App
import app.bertsatlas.AppController
import app.bertsatlas.Destination
import app.bertsatlas.IncomingPackFiles
import app.bertsatlas.data.buildBertsAtlasDatabase
import app.bertsatlas.data.createPreferencesStore
import app.bertsatlas.data.getDatabaseBuilder
import app.bertsatlas.desktop.editor.EditorWindow
import java.awt.Desktop
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JOptionPane

private var activeController: AppController? = null

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun main() {
    System.setProperty("apple.awt.application.name", APP_NAME)
    val paths = DesktopPaths.forCurrentUser()
    listenForSystemEvents()
    val database = getDatabaseBuilder(paths.databaseFile).buildBertsAtlasDatabase()
    val preferences = createPreferencesStore(paths.preferencesFile.absolutePath)
    val debugBuild = System.getProperty("bertsatlas.debug").toBoolean()
    val commandLineTool = CommandLineTool.forThisApp()

    application {
        val windowState = rememberWindowState(size = DpSize(1180.dp, 820.dp))
        var dropHighlighted by remember { mutableStateOf(false) }
        var editorPackId by remember { mutableStateOf<String?>(null) }
        var editorOpen by remember { mutableStateOf(System.getProperty("bertsatlas.openEditor").toBoolean()) }
        val dropTarget = remember {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) { dropHighlighted = true }
                override fun onExited(event: DragAndDropEvent) { dropHighlighted = false }
                override fun onEnded(event: DragAndDropEvent) { dropHighlighted = false }
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    dropHighlighted = false
                    val pack = droppedFiles(event).firstOrNull { isPackFileName(it.name) } ?: return false
                    IncomingPackFiles.deliver(pack.absolutePath)
                    return true
                }
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            state = windowState,
            onPreviewKeyEvent = { event ->
                val controller = activeController
                when {
                    controller == null -> false
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> controller.handleBack()
                    else -> QuizKeys.handle(controller, event)
                }
            },
        ) {
            window.minimumSize = Dimension(MINIMUM_WIDTH, MINIMUM_HEIGHT)
            val filePicker = remember { DesktopPackFilePicker { window } }
            val packSharer = remember { DesktopPackSharer { window } }
            MenuBar {
                Menu("File", mnemonic = 'F') {
                    Item("Import Pack…", shortcut = KeyShortcut(Key.O, meta = true)) { activeController?.pickPackFile() }
                    Item("Edit Packs…", shortcut = KeyShortcut(Key.E, meta = true)) {
                        editorPackId = (activeController?.state?.value?.destination as? Destination.PackHome)?.packId
                        editorOpen = true
                    }
                    Separator()
                    Item("Install Command Line Tool…") { reportInstall(commandLineTool.install(), commandLineTool) }
                }
                Menu("Edit", mnemonic = 'E') {
                    Item("Cut") { sendEditingKey(KeyEvent.VK_X) }
                    Item("Copy") { sendEditingKey(KeyEvent.VK_C) }
                    Item("Paste") { sendEditingKey(KeyEvent.VK_V) }
                    Item("Select All") { sendEditingKey(KeyEvent.VK_A) }
                }
                Menu("Window", mnemonic = 'W') {
                    Item("Minimize", shortcut = KeyShortcut(Key.M, meta = true)) { windowState.isMinimized = true }
                    Item("Zoom") {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    }
                }
            }
            Box(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = ::carriesFiles, target = dropTarget)) {
                App(
                    database,
                    preferences,
                    paths.libraryDirectory.absolutePath,
                    debugBuild,
                    filePicker = filePicker,
                    packSharer = packSharer,
                    watchLibrary = true,
                ) { activeController = it }
                if (dropHighlighted) DropHint(Modifier.align(Alignment.Center))
            }
        }
        if (editorOpen) {
            EditorWindow(paths.libraryDirectory, File(paths.applicationSupport, "editor-work"), editorPackId) { editorOpen = false }
        }
    }
}

private fun listenForSystemEvents() {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
        desktop.setOpenFileHandler { event -> event.files.firstOrNull { isPackFileName(it.name) }?.let { IncomingPackFiles.deliver(it.absolutePath) } }
    }
    if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
        desktop.setPreferencesHandler { activeController?.navigate(Destination.Settings) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun carriesFiles(event: DragAndDropEvent): Boolean = event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

@OptIn(ExperimentalComposeUiApi::class)
private fun droppedFiles(event: DragAndDropEvent): List<File> =
    runCatching { (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>() }.getOrDefault(emptyList())

private fun sendEditingKey(keyCode: Int) {
    val target = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
    val now = System.currentTimeMillis()
    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, KeyEvent.META_DOWN_MASK, keyCode, KeyEvent.CHAR_UNDEFINED))
    target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, KeyEvent.META_DOWN_MASK, keyCode, KeyEvent.CHAR_UNDEFINED))
}

private fun reportInstall(result: CommandLineToolInstall, tool: CommandLineTool) {
    val message = when (result) {
        is CommandLineToolInstall.Installed -> "The bert command is ready. Open Terminal and run: bert --help"
        CommandLineToolInstall.NotBundled -> "This copy of Bert's Atlas doesn't include the bert command."
        CommandLineToolInstall.Cancelled -> return
        is CommandLineToolInstall.Failed -> "The bert command couldn't be installed at ${tool.linkFile.absolutePath}. ${result.reason}"
    }
    JOptionPane.showMessageDialog(null, message, "Command Line Tool", JOptionPane.INFORMATION_MESSAGE)
}

@androidx.compose.runtime.Composable
private fun DropHint(modifier: Modifier) {
    Box(
        modifier
            .background(Color(0xEE2A5F58), RoundedCornerShape(20.dp))
            .border(2.dp, Color(0xFFF5E9DF), RoundedCornerShape(20.dp))
            .padding(horizontal = 32.dp, vertical = 22.dp),
    ) {
        BasicText("Drop to import this pack", style = TextStyle(color = Color(0xFFF5E9DF), fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
    }
}

private const val APP_NAME = "Bert's Atlas"
private const val MINIMUM_WIDTH = 480
private const val MINIMUM_HEIGHT = 640
