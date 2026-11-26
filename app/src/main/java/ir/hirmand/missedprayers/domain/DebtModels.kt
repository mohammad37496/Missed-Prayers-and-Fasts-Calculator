package ir.hirmand.missedprayers.domain

enum class PrayerType(val title: String) {
    FAJR("صبح"), DHUHR("ظهر"), ASR("عصر"), MAGHRIB("مغرب"), ISHA("عشا")
}

data class DebtProgress(val total: Int, val completed: Int) {
    init {
        require(total >= 0) { "تعداد کل نمی‌تواند منفی باشد" }
        require(completed >= 0) { "تعداد انجام‌شده نمی‌تواند منفی باشد" }
        require(completed <= total) { "تعداد انجام‌شده نمی‌تواند از کل بیشتر باشد" }
    }

    val remaining: Int get() = total - completed
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total

    fun record(amount: Int = 1): DebtProgress {
        require(amount > 0) { "مقدار ثبت باید بیشتر از صفر باشد" }
        val nextCompleted = (completed.toLong() + amount.toLong()).coerceAtMost(total.toLong()).toInt()
        return copy(completed = nextCompleted)
    }
}

object DebtCalculator {
    fun fromUserEstimate(total: Int, alreadyCompleted: Int = 0): DebtProgress =
        DebtProgress(total = total, completed = alreadyCompleted)
}
