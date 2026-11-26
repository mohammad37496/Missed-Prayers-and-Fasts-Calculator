package ir.hirmand.missedprayers.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hirmand.missedprayers.data.EncryptedBackupCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class BackupRecoveryState(
    val restoring: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false
)

/** Owns the emergency SAF restore so Activity recreation cannot cancel file I/O or leak the one-shot password. */
class BackupRecoveryViewModel : ViewModel() {
    private var pendingPassword: CharArray? = null
    private val _state = MutableStateFlow(BackupRecoveryState())
    val state = _state.asStateFlow()

    fun holdPassword(password: CharArray) {
        pendingPassword?.fill('\u0000')
        pendingPassword = password
        _state.value = _state.value.copy(error = null, completed = false)
    }

    fun cancelPending(message: String) {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
        _state.value = BackupRecoveryState(error = message)
    }

    fun restoreFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        onRestoreBackup: suspend (ByteArray, CharArray) -> Unit
    ) {
        val password = pendingPassword.also { pendingPassword = null }
        if (password == null) {
            _state.value = BackupRecoveryState(error = "گذرواژه بازیابی در دسترس نیست؛ دوباره تلاش کنید.")
            return
        }
        _state.value = BackupRecoveryState(restoring = true)
        val restoreJob = viewModelScope.launch {
            var bytes: ByteArray? = null
            try {
                val backupBytes = withContext(Dispatchers.IO) { readEncryptedBackup(contentResolver, uri) }
                bytes = backupBytes
                onRestoreBackup(backupBytes, password)
                _state.value = BackupRecoveryState(completed = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = BackupRecoveryState(error = error.message?.take(180) ?: "بازیابی انجام نشد؛ پایگاه داده قبلی دست‌نخورده باقی مانده است.")
            } finally {
                bytes?.fill(0)
                password.fill('\u0000')
                if (_state.value.restoring) _state.value = _state.value.copy(restoring = false)
            }
        }
        // Completion handlers also run when cancellation happens before the coroutine body starts.
        restoreJob.wipeOnCompletion(password)
    }

    fun consumeCompletion() {
        if (_state.value.completed) _state.value = _state.value.copy(completed = false)
    }

    override fun onCleared() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
    }

    private fun readEncryptedBackup(contentResolver: ContentResolver, uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("فایل پشتیبان قابل خواندن نیست")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= EncryptedBackupCodec.MAX_FILE_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
}
