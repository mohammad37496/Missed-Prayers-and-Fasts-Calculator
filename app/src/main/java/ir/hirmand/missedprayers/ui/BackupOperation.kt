package ir.hirmand.missedprayers.ui

import kotlinx.coroutines.CancellationException

/** Converts ordinary backup failures to a Result while always wiping the one-shot password. */
internal suspend fun <T> runBackupOperation(
    password: CharArray,
    operation: suspend () -> T
): Result<T> = try {
    Result.success(operation())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
} finally {
    password.fill('\u0000')
}
