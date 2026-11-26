package ir.hirmand.missedprayers.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.hirmand.missedprayers.R
import ir.hirmand.missedprayers.data.AppDatabase
import ir.hirmand.missedprayers.data.PlannerSettingsEntity
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val current = try {
            AppDatabase.get(applicationContext).debtDao().observePlannerSettings().first()
        } catch (_: Exception) {
            return Result.retry()
        } ?: return Result.success()
        if (!current.reminderEnabled) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "یادآور عبادات", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "یادآوری برنامه شخصی جبران"
        })
        val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val permitted = runtimePermissionGranted && NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        if (permitted) {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("یادآوری برنامه جبران")
                .setContentText("اگر فرصت دارید، هدف امروزتان را با آرامش پیگیری کنید.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
        val zoneId = runCatching { AppDatabase.get(applicationContext).debtDao().getProfileSnapshot()?.timezoneId ?: ZoneId.systemDefault().id }
            .getOrDefault(ZoneId.systemDefault().id)
        ReminderScheduler.scheduleNext(applicationContext, current, zoneId)
        return Result.success()
    }

    companion object { private const val CHANNEL_ID = "worship_reminders"; private const val NOTIFICATION_ID = 2401 }
}

object ReminderScheduler {
    private const val UNIQUE_WORK = "daily_worship_reminder"

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK) }

    fun schedule(context: Context, settings: PlannerSettingsEntity, zoneId: String = ZoneId.systemDefault().id) {
        if (!settings.reminderEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        val delay = nextDelayMillis(settings, ZonedDateTime.now(zone))
        enqueue(context, delay, ExistingWorkPolicy.REPLACE)
    }

    internal fun scheduleNext(context: Context, settings: PlannerSettingsEntity, zoneId: String = ZoneId.systemDefault().id) {
        if (!settings.reminderEnabled) return
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        enqueue(context, nextDelayMillis(settings, ZonedDateTime.now(zone)), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(context: Context, delay: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    internal fun nextDelayMillis(settings: PlannerSettingsEntity, now: LocalDateTime): Long =
        nextDelayMillis(settings, now.atZone(ZoneId.systemDefault()))

    internal fun nextDelayMillis(settings: PlannerSettingsEntity, now: ZonedDateTime): Long {
        require(settings.activeWeekdaysMask in 1..127 && settings.reminderHour in 0..23 && settings.reminderMinute in 0..59)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val bit = 1 shl (date.dayOfWeek.value - 1)
            if (settings.activeWeekdaysMask and bit == 0) continue
            val localTarget = date.atTime(settings.reminderHour, settings.reminderMinute)
            var target = localTarget.atZone(now.zone)
            if (now.zone.rules.getValidOffsets(localTarget).size == 2 && !target.toInstant().isAfter(now.toInstant())) {
                target = target.withLaterOffsetAtOverlap()
            }
            if (target.toInstant().isAfter(now.toInstant())) {
                return Duration.between(now.toInstant(), target.toInstant()).toMillis().coerceAtLeast(1)
            }
        }
        return Duration.ofDays(1).toMillis()
    }
}
