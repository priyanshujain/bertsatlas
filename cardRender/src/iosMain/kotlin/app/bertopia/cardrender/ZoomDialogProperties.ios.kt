package app.bertopia.cardrender

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

internal actual fun zoomDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false, scrimColor = Color.Transparent)
