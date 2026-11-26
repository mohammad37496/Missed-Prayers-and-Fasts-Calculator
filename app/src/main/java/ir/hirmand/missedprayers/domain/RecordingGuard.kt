package ir.hirmand.missedprayers.domain

/** Prevents concurrent and accidental rapid duplicate submissions for the same action. */
internal class RecordingGuard(private val debounceMillis: Long = 600L) {
    private val inFlight = mutableSetOf<String>()
    private val lastAcceptedAt = mutableMapOf<String, Long>()

    init { require(debounceMillis >= 0L) }

    @Synchronized
    fun begin(key: String, nowMillis: Long): Boolean {
        require(key.isNotBlank() && nowMillis >= 0L)
        if (key in inFlight) return false
        val last = lastAcceptedAt[key]
        if (last != null && nowMillis - last < debounceMillis) return false
        inFlight += key
        lastAcceptedAt[key] = nowMillis
        return true
    }

    @Synchronized
    fun finish(key: String, successful: Boolean) {
        inFlight -= key
        if (!successful) lastAcceptedAt.remove(key)
    }
}
