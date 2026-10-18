package app.bertsatlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.GameController.GCKeyboard
import platform.GameController.GCKeyboardDidConnectNotification
import platform.GameController.GCKeyboardDidDisconnectNotification

@Composable
actual fun hardwareKeyboardPresent(): Boolean {
    var connected by remember { mutableStateOf(GCKeyboard.coalescedKeyboard != null) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(GCKeyboardDidConnectNotification, GCKeyboardDidDisconnectNotification).map { name ->
            center.addObserverForName(name, null, null) { _ -> connected = GCKeyboard.coalescedKeyboard != null }
        }
        onDispose { observers.forEach(center::removeObserver) }
    }
    return connected
}
