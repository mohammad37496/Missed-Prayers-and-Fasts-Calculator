package ir.hirmand.missedprayers.ui

import kotlinx.coroutines.Job

/** Wipes one-shot sensitive buffers even when a coroutine is cancelled before its body starts. */
internal fun Job.wipeOnCompletion(buffer: CharArray) {
    invokeOnCompletion { buffer.fill('\u0000') }
}

/** Wipes one-shot byte buffers even when a coroutine is cancelled before its body starts. */
internal fun Job.wipeOnCompletion(buffer: ByteArray) {
    invokeOnCompletion { buffer.fill(0) }
}
