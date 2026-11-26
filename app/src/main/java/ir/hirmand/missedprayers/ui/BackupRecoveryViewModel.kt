package ir.hirmand.missedprayers.ui

import androidx.lifecycle.ViewModel

/** Holds the one-shot restore password across Activity recreation without saving it in a Bundle. */
class BackupRecoveryViewModel : ViewModel() {
    private var pendingPassword: CharArray? = null

    fun holdPassword(password: CharArray) {
        pendingPassword?.fill('\u0000')
        pendingPassword = password
    }

    fun takePassword(): CharArray? = pendingPassword.also { pendingPassword = null }

    override fun onCleared() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
        super.onCleared()
    }
}
