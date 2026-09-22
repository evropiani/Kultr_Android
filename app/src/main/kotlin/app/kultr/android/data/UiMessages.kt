package app.kultr.android.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MessageKind { INFO, SUCCESS, WARNING, ERROR }

data class UiMessage(val text: String, val kind: MessageKind = MessageKind.INFO, val long: Boolean = false)

/** Toast-style messages from anywhere in the app, shown by the UI's snackbar host. */
class UiMessages {
    private val flow = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UiMessage> = flow.asSharedFlow()

    fun show(text: String, kind: MessageKind = MessageKind.INFO, long: Boolean = false) {
        flow.tryEmit(UiMessage(text, kind, long))
    }

    fun error(text: String) = show(text, MessageKind.ERROR, long = true)
    fun success(text: String) = show(text, MessageKind.SUCCESS)
}
