package ir.hirmand.missedprayers.ui

import ir.hirmand.missedprayers.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes as PlayerAudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ir.hirmand.missedprayers.data.DailyVerseResult
import ir.hirmand.missedprayers.data.ExcusePeriodEntity
import ir.hirmand.missedprayers.data.FastingDebtEntity
import ir.hirmand.missedprayers.data.FiqhPreferencesEntity
import ir.hirmand.missedprayers.data.PlannerSettingsEntity
import ir.hirmand.missedprayers.data.PrayerDebtEntity
import ir.hirmand.missedprayers.data.UserProfileEntity
import ir.hirmand.missedprayers.domain.CalendarDates
import ir.hirmand.missedprayers.domain.CalendarDate
import ir.hirmand.missedprayers.domain.DebtEstimate
import ir.hirmand.missedprayers.domain.DebtEstimateEngine
import ir.hirmand.missedprayers.domain.FiqhEvaluationContext
import ir.hirmand.missedprayers.domain.RecordedPeriod
import ir.hirmand.missedprayers.domain.PlanProjection
import ir.hirmand.missedprayers.domain.estimatedActiveDays
import ir.hirmand.missedprayers.domain.PrayerType
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun calendarLabel(preference: String): String = when (preference) {
    "PERSIAN" -> "شمسی"
    "ISLAMIC_CIVIL" -> "قمری محاسباتی"
    else -> "میلادی"
}

private fun parseCalendarInput(value: String, preference: String): LocalDate {
    val parts = value.trim().split(Regex("[-/]"))
    require(parts.size == 3)
    return CalendarDates.fromPreferred(CalendarDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()), preference)
}

private fun formatCalendarInput(date: LocalDate, preference: String): String {
    val value = CalendarDates.toPreferred(date, preference)
    return "%04d-%02d-%02d".format(Locale.ROOT, value.year, value.month, value.day)
}

private fun reformatCalendarInput(value: String, from: String, to: String): String =
    if (value.isBlank()) "" else formatCalendarInput(parseCalendarInput(value, from), to)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfileEntity?, periods: List<ExcusePeriodEntity>,
    onSave: (UserProfileEntity) -> Unit,
    onAddPeriod: suspend (String, LocalDate, LocalDate, String?) -> Boolean,
    onDeletePeriod: (Long) -> Unit,
    onApplyEstimate: suspend (UserProfileEntity, Int, Int) -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.displayName.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender ?: "UNSPECIFIED") }
    var calendar by remember(profile) { mutableStateOf(profile?.calendarPreference ?: "PERSIAN") }
    var start by remember(profile) {
        mutableStateOf(profile?.let { saved -> saved.taklifStartDate?.let { raw -> runCatching { formatCalendarInput(LocalDate.parse(raw), saved.calendarPreference) }.getOrDefault(raw) } }.orEmpty())
    }
    var end by remember(profile) {
        mutableStateOf(profile?.let { saved -> saved.taklifEndDate?.let { raw -> runCatching { formatCalendarInput(LocalDate.parse(raw), saved.calendarPreference) }.getOrDefault(raw) } }.orEmpty())
    }
    var timezoneId by remember(profile) { mutableStateOf(profile?.timezoneId ?: ZoneId.systemDefault().id) }
    var periodKind by remember { mutableStateOf("MENSTRUATION") }
    var periodStart by remember { mutableStateOf("") }
    var periodEnd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val estimatePreviewResult by produceState<Result<DebtEstimate>?>(null, start, end, calendar, timezoneId, gender, periods) {
        value = null
        if (start.isNotBlank()) {
            delay(200)
            value = withContext(Dispatchers.Default) {
                runCatching {
                    val today = CalendarDates.today(timezoneId)
                    val from = parseCalendarInput(start, calendar)
                    val to = end.takeIf(String::isNotBlank)?.let { parseCalendarInput(it, calendar) } ?: today
                    require(!from.isAfter(today) && !to.isAfter(today)) { "بازه برآورد نمی‌تواند شامل تاریخ آینده باشد" }
                    DebtEstimateEngine.estimate(from, to, FiqhEvaluationContext(
                        gender,
                        periods.map { RecordedPeriod(it.kind, LocalDate.parse(it.startDate), LocalDate.parse(it.endDate)) }
                    ))
                }
            }
        }
    }
    val estimatePreview = estimatePreviewResult?.getOrNull()
    val rawPrayerEstimate = estimatePreview?.prayerUnitsByType?.get(PrayerType.FAJR) ?: 0
    val rawFastingEstimate = estimatePreview?.fastingDays ?: 0

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("پروفایل و برآورد شخصی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("اطلاعات زیر برای سازمان‌دهی برآورد شماست؛ به‌تنهایی حکم شرعی یا بدهی قطعی ایجاد نمی‌کند.", style = MaterialTheme.typography.bodyMedium)
        }
        item { OutlinedTextField(name, { name = it.take(60) }, label = { Text("نام نمایشی (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("جنسیت (اختیاری)", fontWeight = FontWeight.SemiBold) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("UNSPECIFIED" to "مشخص نشده", "WOMAN" to "خانم", "MAN" to "آقا", "PREFER_NOT_TO_SAY" to "ترجیح می‌دهم نگویم").forEach { (value, label) ->
                    OutlinedButton(onClick = { gender = value }) { Text(if (gender == value) "✓ $label" else label) }
                }
            }
        }
        item { OutlinedTextField(start, { start = it }, label = { Text("شروع بازه برآورد (${calendarLabel(calendar)} YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(end, { end = it }, label = { Text("پایان بازه برآورد اختیاری (${calendarLabel(calendar)} YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(timezoneId, { timezoneId = it.take(80) }, label = { Text("منطقه زمانی IANA (مثلاً Asia/Tehran)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("تقویم پیش‌فرض") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("PERSIAN" to "شمسی", "ISLAMIC_CIVIL" to "قمری محاسباتی", "GREGORIAN" to "میلادی").forEach { (value, label) ->
                    OutlinedButton(onClick = {
                        runCatching {
                            listOf(
                                reformatCalendarInput(start, calendar, value),
                                reformatCalendarInput(end, calendar, value),
                                reformatCalendarInput(periodStart, calendar, value),
                                reformatCalendarInput(periodEnd, calendar, value)
                            )
                        }.onSuccess { converted ->
                            start = converted[0]
                            end = converted[1]
                            periodStart = converted[2]
                            periodEnd = converted[3]
                            calendar = value
                            error = null
                        }.onFailure {
                            error = "یکی از تاریخ‌ها در تقویم فعلی معتبر نیست؛ ابتدا آن را اصلاح کنید. تقویم تغییر نکرد."
                        }
                    }) { Text(if (calendar == value) "✓ $label" else label) }
                }
            }
        }
        item {
            Button(onClick = {
                runCatching {
                    val startIso = start.takeIf(String::isNotBlank)?.let { parseCalendarInput(it, calendar).toString() }
                    val endIso = end.takeIf(String::isNotBlank)?.let { parseCalendarInput(it, calendar).toString() }
                    ZoneId.of(timezoneId)
                    onSave(UserProfileEntity(displayName = name.ifBlank { null }, gender = gender, taklifStartDate = startIso, taklifEndDate = endIso, timezoneId = timezoneId, calendarPreference = calendar, marjaId = profile?.marjaId, updatedAt = System.currentTimeMillis()))
                    error = null
                }.onFailure { error = "تاریخ را با قالب YYYY-MM-DD و به‌صورت معتبر وارد کنید." }
            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره پروفایل") }
        }
        if (estimatePreview != null) item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پیش‌نمایش برآورد خام", fontWeight = FontWeight.Bold)
                    Text("بر اساس بازه انتخاب‌شده: برای هر یک از پنج نماز $rawPrayerEstimate مورد و برای روزه $rawFastingEstimate روز برآورد خام ثبت شده است.")
                    Text("نماز به تفکیک سال میلادی: ${estimatePreview.prayerDaysByGregorianYear.entries.joinToString(" · ") { "${it.key}: ${it.value}" }}", style = MaterialTheme.typography.bodySmall)
                    Text("روزه به تفکیک سال میلادی: ${estimatePreview.fastingDaysByGregorianYear.entries.joinToString(" · ") { "${it.key}: ${it.value}" }}", style = MaterialTheme.typography.bodySmall)
                    Text("روزهای نیازمند بررسی فقهی: نماز ${estimatePreview.prayerDaysNeedingReview} روز · روزه ${estimatePreview.fastingDaysNeedingReview} روز. روزهای عذر، تفاوت تکلیف روزه و قواعد مرجع هنوز اعمال نشده‌اند؛ هیچ حکم شرعی از این برآورد نتیجه نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        val preparedProfile = runCatching {
                            val startIso = start.takeIf(String::isNotBlank)?.let { parseCalendarInput(it, calendar).toString() }
                            val endIso = end.takeIf(String::isNotBlank)?.let { parseCalendarInput(it, calendar).toString() }
                            ZoneId.of(timezoneId)
                            UserProfileEntity(displayName = name.ifBlank { null }, gender = gender, taklifStartDate = startIso, taklifEndDate = endIso, timezoneId = timezoneId, calendarPreference = calendar, marjaId = profile?.marjaId, updatedAt = System.currentTimeMillis())
                        }
                        preparedProfile.onSuccess { value ->
                            scope.launch {
                                runCatching { onApplyEstimate(value, rawPrayerEstimate, rawFastingEstimate) }
                                    .onSuccess { error = null }
                                    .onFailure { error = it.message ?: "اعمال برآورد و ذخیره پروفایل انجام نشد؛ اطلاعات قبلی حفظ شده است." }
                            }
                        }.onFailure { error = "اطلاعات پروفایل یا تاریخ‌ها معتبر نیستند؛ هیچ تغییری اعمال نشد." }
                    }, modifier = Modifier.fillMaxWidth()) { Text("اعمال برآورد خام و حفظ سوابق") }
                }
            }
        }
        if (start.isNotBlank() && estimatePreviewResult?.isFailure == true) {
            item { Text(estimatePreviewResult?.exceptionOrNull()?.message ?: "تاریخ‌های بازه برآورد معتبر نیستند.", color = MaterialTheme.colorScheme.error) }
        }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ثبت روزهای عذر یا بازه‌های شخصی", fontWeight = FontWeight.Bold)
                    Text("این بازه‌ها فقط ثبت می‌شوند و تا زمان انتخاب قاعده فقهی مستند، به‌طور خودکار از محاسبه کم نخواهند شد.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MENSTRUATION" to "عادت ماهانه", "POSTPARTUM" to "نفاس", "ILLNESS" to "بیماری", "OTHER_USER_RECORDED" to "سایر").forEach { (value, label) ->
                            OutlinedButton(onClick = { periodKind = value }) { Text(if (periodKind == value) "✓ $label" else label) }
                        }
                    }
                    OutlinedTextField(periodStart, { periodStart = it }, label = { Text("شروع ${calendarLabel(calendar)} YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(periodEnd, { periodEnd = it }, label = { Text("پایان ${calendarLabel(calendar)} YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val parsed = runCatching { parseCalendarInput(periodStart, calendar) to parseCalendarInput(periodEnd, calendar) }
                        parsed.onSuccess { (startDate, endDate) ->
                            scope.launch {
                                runCatching { onAddPeriod(periodKind, startDate, endDate, null) }
                                    .onSuccess { added -> if (added) { periodStart = ""; periodEnd = ""; error = null } }
                                    .onFailure { error = it.message ?: "ثبت بازه انجام نشد؛ اطلاعات واردشده حفظ شده است." }
                            }
                        }.onFailure { error = "بازه تاریخ ${calendarLabel(calendar)} معتبر نیست یا پایان قبل از شروع است." }
                    }, modifier = Modifier.fillMaxWidth()) { Text("ثبت بازه") }
                }
            }
        }
        if (periods.isNotEmpty()) item { Text("بازه‌های ثبت‌شده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(periods, key = { it.id }) { period ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(periodKindLabel(period.kind), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${runCatching { formatCalendarInput(LocalDate.parse(period.startDate), calendar) }.getOrDefault(period.startDate)} تا ${runCatching { formatCalendarInput(LocalDate.parse(period.endDate), calendar) }.getOrDefault(period.endDate)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = { onDeletePeriod(period.id) }) { Text("حذف") }
                }
            }
        }
    }
}

@Composable
fun PlannerScreen(
    settings: PlannerSettingsEntity?,
    prayers: List<Pair<String, Int>>,
    fastingRemaining: Int,
    timezoneId: String?,
    calendarPreference: String?,
    notificationPermissionGranted: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onSave: (PlannerSettingsEntity) -> Unit
) {
    var prayerTarget by remember(settings) { mutableStateOf((settings?.dailyPrayerTarget ?: 5).toString()) }
    var fastingTarget by remember(settings) { mutableStateOf((settings?.dailyFastingTarget ?: 1).toString()) }
    var mask by remember(settings) { mutableIntStateOf(settings?.activeWeekdaysMask ?: 127) }
    var reminders by remember(settings) { mutableStateOf(settings?.reminderEnabled ?: false) }
    var hour by remember(settings) { mutableStateOf((settings?.reminderHour ?: 20).toString()) }
    var minute by remember(settings) { mutableStateOf((settings?.reminderMinute ?: 0).toString()) }
    val remaining = prayers.sumOf { it.second.toLong() }
    val projection = runCatching { PlanProjection(remaining, prayerTarget.toInt(), mask) }.getOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("برنامه‌ریز جبران", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { Text("هدف‌ها را خودتان تعیین می‌کنید. برآورد زمان پایان، برنامه‌ای تقریبی است و روزهای انتخابی هفته را لحاظ می‌کند.") }
        item { OutlinedTextField(prayerTarget, { prayerTarget = it.filter(Char::isDigit).take(3) }, label = { Text("هدف نماز در هر روز فعال") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(fastingTarget, { fastingTarget = it.filter(Char::isDigit).take(2) }, label = { Text("هدف روزه در هر روز فعال") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        item { Text("روزهای فعال هفته") }
        item {
            Column {
                listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه").forEachIndexed { index, label ->
                    val checked = mask and (1 shl index) != 0
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { value -> mask = if (value) mask or (1 shl index) else mask and (1 shl index).inv() }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(label)
                    }
                }
            }
        }
        item { if (projection != null) Text("با $remaining نماز باقی‌مانده، حدود ${projection.estimatedActiveDays()} روز فعال و پایان تقریبی ${CalendarDates.displayPreferred(projection.estimatedCompletionDate(CalendarDates.today(timezoneId ?: ZoneId.systemDefault().id)), calendarPreference ?: "PERSIAN")} لازم است.") }
        item { Text("روزه باقی‌مانده: $fastingRemaining روز؛ با هدف ${fastingTarget.toIntOrNull() ?: 0} در روز فعال، زمان تقریبی ${estimatedActiveDays(fastingRemaining.toLong(), (fastingTarget.toIntOrNull() ?: 1).coerceAtLeast(1))} روز فعال است.") }
        item { Text("یادآورها با WorkManager زمان‌بندی می‌شوند و ممکن است به‌دلیل حالت ذخیره باتری یا محدودیت سیستم‌عامل با تأخیر اجرا شوند.", style = MaterialTheme.typography.bodySmall) }
        item {
            if (reminders && notificationPermissionGranted) {
                Text("اعلان‌های برنامه فعال هستند؛ یادآور طبق برنامه زمان‌بندی می‌شود.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            } else if (reminders) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مجوز یا نمایش اعلان‌های برنامه غیرفعال است؛ تا فعال‌کردن آن در تنظیمات دستگاه، اعلانی نمایش داده نمی‌شود.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("تنظیم مجوز اعلان") }
                }
            } else {
                Text("یادآورها خاموش هستند.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = reminders,
                    role = Role.Checkbox,
                    onValueChange = { reminders = it }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = reminders, onCheckedChange = null)
                Text("یادآوری روزانه فعال باشد")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("ساعت") }, modifier = Modifier.weight(1f))
                OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("دقیقه") }, modifier = Modifier.weight(1f))
            }
        }
        item { Button(onClick = { onSave(PlannerSettingsEntity(dailyPrayerTarget = prayerTarget.toIntOrNull() ?: 5, dailyFastingTarget = fastingTarget.toIntOrNull() ?: 1, activeWeekdaysMask = mask, reminderEnabled = reminders, reminderHour = hour.toIntOrNull() ?: 20, reminderMinute = minute.toIntOrNull() ?: 0, updatedAt = System.currentTimeMillis())) }, modifier = Modifier.fillMaxWidth(), enabled = projection != null && fastingTarget.toIntOrNull() in 1..30 && hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59) { Text("ذخیره برنامه و یادآورها") }
    }
    }
}

@Composable
fun CalendarScreen(profile: UserProfileEntity?) {
    val zone = profile?.timezoneId ?: ZoneId.systemDefault().id
    val preference = profile?.calendarPreference ?: "PERSIAN"
    val today = CalendarDates.today(zone)
    val todayPreferred = CalendarDates.toPreferred(today, preference)
    val todayPersian = CalendarDates.toPersian(today)
    val todayIslamic = CalendarDates.toIslamicCivil(today)
    var shownMonth by remember(preference, todayPreferred.year, todayPreferred.month) { mutableStateOf(CalendarDate(todayPreferred.year, todayPreferred.month, 1)) }
    var selectedDate by remember(today) { mutableStateOf(today) }
    fun shiftMonth(delta: Int) {
        val absolute = shownMonth.year.toLong() * 12 + shownMonth.month - 1 + delta
        val year = Math.floorDiv(absolute, 12).toInt()
        val month = Math.floorMod(absolute, 12L).toInt() + 1
        val firstDay = CalendarDate(year, month, 1)
        shownMonth = firstDay
        selectedDate = CalendarDates.fromPreferred(firstDay, preference)
    }
    val monthStart = CalendarDates.fromPreferred(shownMonth, preference)
    val nextMonth = if (shownMonth.month == 12) CalendarDate(shownMonth.year + 1, 1, 1) else CalendarDate(shownMonth.year, shownMonth.month + 1, 1)
    val monthDays = ChronoUnit.DAYS.between(monthStart, CalendarDates.fromPreferred(nextMonth, preference)).toInt()
    val leading = (monthStart.dayOfWeek.value + 1) % 7 // Saturday is the first column.
    val selectedPreferred = CalendarDates.toPreferred(selectedDate, preference)
    val selectedPersian = CalendarDates.toPersian(selectedDate)
    val selectedIslamic = CalendarDates.toIslamicCivil(selectedDate)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("تقویم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("امروز · ${calendarLabel(preference)} ${todayPreferred.year}/${todayPreferred.month}/${todayPreferred.day}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                Text("میلادی ${today.format(DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.forLanguageTag("fa-IR")))}", color = MaterialTheme.colorScheme.onPrimary)
                Text("شمسی ${todayPersian.year}/${todayPersian.month}/${todayPersian.day}", color = MaterialTheme.colorScheme.onPrimary)
                Text("قمری محاسباتی ${todayIslamic.year}/${todayIslamic.month}/${todayIslamic.day}", color = MaterialTheme.colorScheme.onPrimary)
                Text("منطقه زمانی: $zone", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { shiftMonth(-1) }) { Text("ماه قبل") }
            Text("${calendarLabel(preference)} · ${shownMonth.year} · ماه ${shownMonth.month}", fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { shiftMonth(1) }) { Text("ماه بعد") }
        }
        OutlinedButton(onClick = { shownMonth = CalendarDate(todayPreferred.year, todayPreferred.month, 1); selectedDate = today }) { Text("بازگشت به امروز") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { Text(it, fontWeight = FontWeight.Bold) }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(42) { index ->
                val day = index - leading + 1
                if (day in 1..monthDays) {
                    val preferredDate = CalendarDate(shownMonth.year, shownMonth.month, day)
                    val gregorian = CalendarDates.fromPreferred(preferredDate, preference)
                    val isSelected = gregorian == selectedDate
                    Card(onClick = { selectedDate = gregorian }, modifier = Modifier.semantics { selected = isSelected }, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.toString(), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (gregorian == today) Text("امروز", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else Spacer(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("تاریخ انتخاب‌شده", fontWeight = FontWeight.Bold)
                Text("${calendarLabel(preference)}: ${selectedPreferred.year}/${selectedPreferred.month}/${selectedPreferred.day}")
                Text("شمسی: ${selectedPersian.year}/${selectedPersian.month}/${selectedPersian.day}")
                Text("میلادی: $selectedDate")
                Text("قمری مدنی: ${selectedIslamic.year}/${selectedIslamic.month}/${selectedIslamic.day}")
            }
        }
        Text("تاریخ قمری بر اساس تقویم محاسباتی مدنی است و ممکن است با رؤیت هلال یا تقویم رسمی محل زندگی تفاوت داشته باشد.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    prayers: List<PrayerDebtEntity>,
    fasting: FastingDebtEntity?,
    records: List<ir.hirmand.missedprayers.data.DebtRecordEntity>,
    calendarPreference: String = "PERSIAN",
    timezoneId: String? = null,
    onUndoRecord: (Long) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var recordFilter by remember { mutableStateOf("ALL") }
    val zone = remember(timezoneId) { runCatching { ZoneId.of(timezoneId ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault()) }
    val prayerCompleted = prayers.sumOf { it.completed.toLong() }
    val prayerTotal = prayers.sumOf { it.total.toLong() }
    val fastingCompleted = fasting?.completed ?: 0
    val fastingTotal = fasting?.total ?: 0
    val filteredRecords = records.filter { record ->
        val matchesFilter = when (recordFilter) {
            "PRAYER" -> record.reference != "FASTING"
            "FASTING" -> record.reference == "FASTING"
            "ADJUSTMENT" -> record.kind.endsWith("ADJUSTMENT")
            else -> true
        }
        val localizedReference = when (record.reference) {
            "FAJR" -> "صبح فجر"
            "DHUHR" -> "ظهر"
            "ASR" -> "عصر"
            "MAGHRIB" -> "مغرب"
            "ISHA" -> "عشا"
            "FASTING" -> "روزه"
            else -> record.reference
        }
        val localizedKind = if (record.kind.endsWith("ADJUSTMENT")) "اصلاح شمارنده" else "ثبت انجام"
        val recordDate = runCatching { Instant.ofEpochMilli(record.createdAt).atZone(zone).toLocalDate() }.getOrNull()
        val dateText = recordDate?.let { CalendarDates.displayPreferred(it, calendarPreference) }.orEmpty()
        matchesFilter && (
            record.kind.contains(search, true) || record.reference.contains(search, true) ||
                localizedReference.contains(search, true) || localizedKind.contains(search, true) ||
                record.createdAt.toString().contains(search) || recordDate?.toString()?.contains(search, true) == true ||
                dateText.contains(search, true)
            )
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("گزارش پیشرفت", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { Text("نماز: $prayerCompleted از $prayerTotal انجام‌شده · ${ (prayerTotal - prayerCompleted).coerceAtLeast(0) } باقی‌مانده") }
        item { androidx.compose.material3.LinearProgressIndicator(progress = { if (prayerTotal == 0L) 0f else (prayerCompleted.toDouble() / prayerTotal).toFloat() }, modifier = Modifier.fillMaxWidth()) }
        item { Text("روزه: $fastingCompleted از $fastingTotal انجام‌شده · ${(fastingTotal - fastingCompleted).coerceAtLeast(0)} باقی‌مانده") }
        item { androidx.compose.material3.LinearProgressIndicator(progress = { if (fastingTotal == 0) 0f else fastingCompleted.toFloat() / fastingTotal }, modifier = Modifier.fillMaxWidth()) }
        item { WeeklyActivityChart(records, zone) }
        items(prayers, key = { it.prayerType }) { debt ->
            val label = PrayerType.entries.firstOrNull { it.name == debt.prayerType }?.title ?: debt.prayerType
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("نماز $label")
                Text("${debt.completed} انجام‌شده · ${(debt.total - debt.completed).coerceAtLeast(0)} باقی‌مانده")
                androidx.compose.material3.LinearProgressIndicator(progress = { if (debt.total == 0) 0f else debt.completed.toFloat() / debt.total }, modifier = Modifier.fillMaxWidth())
            } }
        }
        item { OutlinedTextField(search, { search = it }, label = { Text("جست‌وجو در تاریخچه") }, modifier = Modifier.fillMaxWidth()) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "همه", "PRAYER" to "نماز", "FASTING" to "روزه", "ADJUSTMENT" to "اصلاح‌ها").forEach { (value, label) ->
                    FilterChip(selected = recordFilter == value, onClick = { recordFilter = value }, label = { Text(label) })
                }
            }
        }
        if (filteredRecords.isEmpty()) {
            item { Text(if (records.isEmpty()) "هنوز سابقه‌ای ثبت نشده است." else "موردی با این جست‌وجو یا فیلتر پیدا نشد.", modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) }
        } else items(filteredRecords, key = { it.id }) { record ->
            val category = if (record.reference == "FASTING") "روزه" else "نماز ${PrayerType.entries.firstOrNull { it.name == record.reference }?.title ?: record.reference}"
            val kindLabel = if (record.kind.endsWith("ADJUSTMENT")) "اصلاح شمارنده" else "ثبت انجام‌شده"
            val localDate = Instant.ofEpochMilli(record.createdAt).atZone(zone).toLocalDate()
            Card {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("$category · $kindLabel", fontWeight = FontWeight.SemiBold)
                        Text(if (record.kind.endsWith("ADJUSTMENT")) "${record.beforeCompleted} ← ${record.afterCompleted}" else "+${record.amount} مورد")
                        Text(CalendarDates.displayPreferred(localDate, calendarPreference), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { onUndoRecord(record.id) }) { Text("بازگردانی") }
                }
            }
        }
    }
}

@Composable
private fun WeeklyActivityChart(records: List<ir.hirmand.missedprayers.data.DebtRecordEntity>, zone: ZoneId) {
    var today by remember(zone) { mutableStateOf(LocalDate.now(zone)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, zone) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now(zone)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(zone) {
        while (true) {
            val nextLocalMidnight = today.plusDays(1).atStartOfDay(zone)
            delay(Duration.between(ZonedDateTime.now(zone).toInstant(), nextLocalMidnight.toInstant()).toMillis().coerceAtLeast(1L))
            today = LocalDate.now(zone)
        }
    }
    val dailyActivity = remember(records, today, zone) {
        val byDate = records.asSequence()
            .filter { it.kind == "PRAYER" || it.kind == "FASTING" }
            .mapNotNull { record ->
                runCatching { Instant.ofEpochMilli(record.createdAt).atZone(zone).toLocalDate() to record }.getOrNull()
            }
            .groupBy({ it.first }, { it.second })
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val entries = byDate[date].orEmpty()
            val prayer = entries.filter { it.kind == "PRAYER" }.sumOf { it.amount.toLong() }
            val fasting = entries.filter { it.kind == "FASTING" }.sumOf { it.amount.toLong() }
            Triple(date, prayer, fasting)
        }
    }
    val maxValue = dailyActivity.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1L) ?: 1L
    val prayerColor = MaterialTheme.colorScheme.primary
    val fastingColor = MaterialTheme.colorScheme.secondary
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("روند ثبت انجام‌ها در هفت روز اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (dailyActivity.all { it.second == 0L && it.third == 0L }) {
                Text("در هفت روز اخیر ثبت انجامی وجود ندارد.", style = MaterialTheme.typography.bodySmall)
            } else {
                Canvas(
                    Modifier.fillMaxWidth().height(132.dp).semantics {
                        contentDescription = dailyActivity.joinToString("؛ ") { (date, prayer, fasting) ->
                            "${date.dayOfMonth}: نماز $prayer، روزه $fasting"
                        }
                    }
                ) {
                    val groupWidth = size.width / dailyActivity.size
                    val barWidth = groupWidth * 0.26f
                    val gap = groupWidth * 0.06f
                    dailyActivity.forEachIndexed { index, entry ->
                        val center = groupWidth * (index + 0.5f)
                        val prayerHeight = (entry.second.toDouble() / maxValue * size.height).toFloat()
                        val fastingHeight = (entry.third.toDouble() / maxValue * size.height).toFloat()
                        drawRect(prayerColor, Offset(center - barWidth - gap / 2f, size.height - prayerHeight), Size(barWidth, prayerHeight))
                        drawRect(fastingColor, Offset(center + gap / 2f, size.height - fastingHeight), Size(barWidth, fastingHeight))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    dailyActivity.forEach { (date, _, _) ->
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(9.dp).background(prayerColor, CircleShape))
                        Text("نماز", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(9.dp).background(fastingColor, CircleShape))
                        Text("روزه", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(fiqh: FiqhPreferencesEntity?, onSave: (FiqhPreferencesEntity) -> Unit, onDeleteAllRequested: () -> Unit) {
    var marja by remember(fiqh) { mutableStateOf(fiqh?.marjaId.orEmpty()) }
    var sourceUrl by remember(fiqh) { mutableStateOf(fiqh?.acceptedSourceUrl.orEmpty()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("تنظیمات و احکام", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { Text("شناسه مرجع تقلید (اختیاری؛ برای ثبت ترجیح شما)") }
        item { OutlinedTextField(marja, { marja = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(48) }, label = { Text("شناسه مرجع") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(sourceUrl, { sourceUrl = it.take(500) }, label = { Text("نشانی رسمی منبع (HTTPS، اختیاری)") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { onSave(FiqhPreferencesEntity(marjaId = marja.ifBlank { null }, rulesetVersion = null, acceptedSourceUrl = sourceUrl.ifBlank { null }, updatedAt = System.currentTimeMillis())) }, enabled = sourceUrl.isBlank() || sourceUrl.startsWith("https://"), modifier = Modifier.fillMaxWidth()) { Text("ذخیره ترجیح مرجع و منبع") } }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وضعیت قواعد فقهی", fontWeight = FontWeight.Bold)
                    Text("در این نسخه هنوز مجموعه قواعد بازبینی‌شده و منبع‌دار برای مراجع مختلف به برنامه متصل نشده است. انتخاب شناسه مرجع به‌معنای اعمال حکم نیست.")
                    Text("برای موارد مربوط به عادت ماهانه، نفاس، بیماری، تأخیر قضا، کفاره و فدیه، تا زمان اتصال منبع معتبر از نتیجه‌گیری خودکار خودداری می‌شود.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { Text("اطلاعات محلی در پایگاه داده رمزگذاری‌شده نگهداری می‌شود. حذف برنامه بدون تهیه نسخه پشتیبان می‌تواند باعث از دست رفتن داده‌ها شود.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("حذف تمام اطلاعات این دستگاه") } }
    }
    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("حذف همه اطلاعات؟") },
        text = { Text("پروفایل، شمارنده‌ها، تاریخچه، برنامه، بازه‌های شخصی و محتوای ذخیره‌شده حذف می‌شوند. این کار برگشت‌پذیر نیست؛ پیش از آن نسخه پشتیبان بگیرید.") },
        confirmButton = { Button(onClick = { showDeleteDialog = false; onDeleteAllRequested() }) { Text("حذف دائمی") } },
        dismissButton = { OutlinedButton(onClick = { showDeleteDialog = false }) { Text("انصراف") } }
    )
}

@Composable
fun DailyVerseCard(result: DailyVerseResult, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val verse = result.verse
    val player = remember(verse?.recitationUrl) {
        if (BuildConfig.DEBUG) verse?.recitationUrl?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(PlayerAudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
        } else null
    }
    var playing by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener); player?.release() }
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("آیه روز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (verse == null) {
                Text(result.message ?: "برای دریافت آیه روز به اینترنت نیاز است.")
                Text("با اتصال دوباره، محتوا را بارگیری کنید.", style = MaterialTheme.typography.bodySmall)
            } else {
                if (result.stale) {
                    Text(
                        "آیه روز جاری در دسترس نیست؛ این آخرین آیه ذخیره‌شده برای تاریخ ${verse.dayKey} است.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("سوره ${verse.surah} · آیه ${verse.ayah}", style = MaterialTheme.typography.labelMedium)
                Text(verse.arabicText, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif), color = MaterialTheme.colorScheme.primary)
                Text(verse.persianTranslation, style = MaterialTheme.typography.bodyLarge)
                Text("ترجمه: ${verse.translationSource} · نسخه ${verse.translationVersion}", style = MaterialTheme.typography.labelSmall)
                verse.translationFootnotes?.takeIf(String::isNotBlank)?.let { footnotes ->
                    Text("پانوشت‌های مترجم", fontWeight = FontWeight.Bold)
                    Text(footnotes, style = MaterialTheme.typography.bodySmall)
                }
                if (BuildConfig.DEBUG && verse.tafsirText != null) {
                    Text("تفسیر", fontWeight = FontWeight.Bold)
                    Text(verse.tafsirText)
                    Text("منبع تفسیر: ${verse.tafsirSource}", style = MaterialTheme.typography.labelSmall)
                } else if (!BuildConfig.DEBUG) {
                    Text("تفسیر فارسی تا تأیید صریح شرایط بازنشر از سوی ناشر در نسخه انتشار فعال نیست.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("برای این آیه، تفسیر فارسی از فهرست منبع دریافت نشد؛ متن بالا فقط ترجمه است.", style = MaterialTheme.typography.bodySmall)
                }
                if (player != null) {
                    Button(onClick = { if (playing) player.pause() else player.play() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (playing) "توقف تلاوت" else "پخش تلاوت آیه")
                    }
                    Text("قاری: ${verse.reciterName ?: "نامشخص"} · پخش آنلاین از سرویس تلاوت متصل؛ فایل صوتی در دستگاه ذخیره نمی‌شود.", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("برای پخش تلاوت عربی، اتصال امن Quran Foundation از طریق backend لازم است؛ کلید API داخل اپ قرار نمی‌گیرد.", style = MaterialTheme.typography.bodySmall)
                }
                Text("متن عربی: Tanzil v1.1 · ترجمه: ${verse.translationSource} · آیه ${verse.surah}:${verse.ayah}", style = MaterialTheme.typography.labelSmall)
                if (result.fromCache) Text("نمایش نسخه ذخیره‌شده · ${result.message ?: "بدون اتصال تازه"}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onRefresh, enabled = !result.loading, modifier = Modifier.fillMaxWidth()) { Text(if (result.loading) "در حال دریافت…" else "به‌روزرسانی آیه روز") }
        }
    }
}

@Composable
fun HadithSourceStatusCard() {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("حدیث نورانی روز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("برای جلوگیری از نقل نادرست، تا اتصال منبع دارای مجوز و ترجمه فارسی قابل‌استناد، حدیثی به‌صورت خودکار نمایش داده نمی‌شود.")
            Text("API رسمی Sunnah.com به کلید دسترسی نیاز دارد و نسخه فعلی آن متن عربی و انگلیسی را ارائه می‌کند، نه فارسی. برای فعال‌سازی این بخش به کلید API و منبع ترجمه فارسی دارای مجوز نیاز است؛ درجه حدیث نیز باید همراه نام ارزیاب از منبع دریافت شود.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun BackupScreen(status: String?, onCreate: (String) -> Unit, onRestore: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("پشتیبان‌گیری امن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { Text("فایل پشتیبان با AES-256-GCM و کلید مشتق‌شده از گذرواژه رمزگذاری می‌شود. گذرواژه در برنامه ذخیره نمی‌شود؛ آن را در جای امن نگه دارید.") }
        item { Text("اگر می‌خواهید گذرواژه پشتیبان را تغییر دهید، یک فایل پشتیبان تازه با گذرواژه جدید بسازید. گذرواژه فراموش‌شده قابل بازیابی نیست و فایل قدیمی همچنان به گذرواژه قبلی نیاز دارد.", style = MaterialTheme.typography.bodySmall) }
        item {
            OutlinedTextField(
                password,
                { password = it.take(128) },
                label = { Text("گذرواژه قوی (حداقل ۱۲ نویسه)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item { Button(onClick = { val value = password; password = ""; onCreate(value) }, enabled = password.length >= 12, modifier = Modifier.fillMaxWidth()) { Text("ساخت فایل پشتیبان رمزگذاری‌شده") } }
        item { Text("بازیابی، اطلاعات فعلی برنامه را با اطلاعات فایل جایگزین می‌کند. ابتدا یک نسخه پشتیبان از اطلاعات فعلی بگیرید.", color = MaterialTheme.colorScheme.error) }
        item {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = confirmReplace,
                    role = Role.Checkbox,
                    onValueChange = { confirmReplace = it }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = confirmReplace, onCheckedChange = null)
                Text("جایگزینی کامل داده‌های فعلی را می‌پذیرم")
            }
        }
        item { OutlinedButton(onClick = { val value = password; password = ""; onRestore(value) }, enabled = password.length >= 12 && confirmReplace, modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل و بازیابی") } }
        if (status != null) item { Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.secondary) }
        item { Text("فایل‌های ناسازگار، ناقص، دست‌کاری‌شده یا دارای گذرواژه اشتباه پیش از شروع تراکنش رد می‌شوند. بازیابی فقط پس از اعتبارسنجی کامل و در یک تراکنش دیتابیس انجام می‌شود.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun MoreScreen(onProfile: () -> Unit, onFiqh: () -> Unit, onBackup: () -> Unit, onHelp: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("بخش‌های بیشتر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("پروفایل و اطلاعات برآورد") }
        Button(onClick = onFiqh, modifier = Modifier.fillMaxWidth()) { Text("تنظیمات فقهی و مدیریت داده‌ها") }
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("پشتیبان‌گیری و بازیابی امن") }
        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text("راهنما و پرسش‌های متداول") }
    }
}

@Composable
fun HelpScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("راهنمای استفاده", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("۱. ثبت برآورد اولیه", fontWeight = FontWeight.Bold)
                    Text("در پروفایل، اطلاعات لازم و بازه تقریبی را وارد کنید. برآورد حاصل تخمینی برای برنامه‌ریزی شخصی است؛ می‌توانید آن را اصلاح کنید و تعدادهای انجام‌شده و سوابق عذرها حفظ می‌شوند.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("۲. ثبت انجام نماز و روزه", fontWeight = FontWeight.Bold)
                    Text("از صفحه خانه، نوع نماز یا روزه و تعداد را ثبت کنید. اصلاح‌های بعدی در تاریخچه می‌مانند. بازگردانی فقط وقتی انجام می‌شود که وضعیت فعلی هنوز با همان ثبت سازگار باشد.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("۳. برنامه‌ریزی و یادآورها", fontWeight = FontWeight.Bold)
                    Text("در صفحه برنامه، هدف روزانه و روزهای فعال را انتخاب کنید. برای نمایش اعلان باید مجوز اعلان اندروید فعال باشد؛ محدودیت باتری یا حالت Doze ممکن است زمان اعلان را جابه‌جا کند.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("۴. تقویم و تاریخ‌ها", fontWeight = FontWeight.Bold)
                    Text("تقویم برنامه تاریخ شمسی، میلادی و قمری محاسباتی را کنار هم نشان می‌دهد. تاریخ قمری محاسباتی ممکن است با رؤیت هلال یا تقویم رسمی محل زندگی متفاوت باشد.")
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("۵. نسخه پشتیبان", fontWeight = FontWeight.Bold)
                    Text("فایل پشتیبان با گذرواژه‌ای که خودتان تعیین می‌کنید رمزگذاری می‌شود. گذرواژه را خارج از دستگاه در جای امن نگه دارید؛ بدون آن بازیابی ممکن نیست. پیش از بازیابی، جایگزینی کامل اطلاعات فعلی را بررسی کنید.")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("یادآوری فقهی مهم", fontWeight = FontWeight.Bold)
                    Text("این برنامه ابزار ثبت و برآورد شخصی است، نه مرجع صدور فتوا. تا زمانی که مرجع تقلید انتخاب و قواعد رسمی او با منبع قابل‌ردیابی بررسی نشده باشند، درباره عادت ماهانه، نفاس، کفاره، فدیه یا مقدار شرعی بدهی حکم قطعی صادر نمی‌شود.")
                }
            }
        }
    }
}

@Composable
fun DatabaseLoadingScreen() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("در حال آماده‌سازی فضای امن و بررسی اطلاعات محلی…")
        }
    }
}

@Composable
fun DatabaseUnavailableScreen(
    keyMaterialMissing: Boolean,
    recoveryViewModel: BackupRecoveryViewModel,
    onRestoreBackup: suspend (ByteArray, CharArray) -> Unit,
    onRecoveryComplete: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    val recoveryState by recoveryViewModel.state.collectAsState()
    LaunchedEffect(recoveryState.completed) {
        if (recoveryState.completed) {
            recoveryViewModel.consumeCompletion()
            onRecoveryComplete()
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            recoveryViewModel.cancelPending("انتخاب فایل بازیابی لغو شد؛ اطلاعات فعلی تغییری نکرد.")
        } else {
            recoveryViewModel.restoreFromUri(context.contentResolver, uri, onRestoreBackup)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Text(
            "دسترسی به پایگاه داده امن ممکن نیست",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (keyMaterialMissing) "اطلاعات کلید محافظت از پایگاه داده یا envelope رمزگذاری‌شده آن پیدا نشد. برای محافظت از اطلاعات، کلید یا passphrase جایگزین ساخته نشده است."
            else "پایگاه داده رمزگذاری‌شده باز نشد. اطلاعات موجود برای جلوگیری از آسیب بیشتر تغییر داده نشده‌اند."
        )
        Text("برنامه را حذف نصب نکنید و داده‌های آن را پاک نکنید. اگر نسخه پشتیبان رمزگذاری‌شده دارید، می‌توانید آن را در یک پایگاه داده تازه بازیابی کنید؛ فایل پایگاه داده قبلی حذف یا بازنویسی نمی‌شود.")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("گذرواژه نسخه پشتیبان") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !recoveryState.restoring,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                recoveryViewModel.holdPassword(password.toCharArray())
                password = ""
                backupPicker.launch(arrayOf("*/*"))
            },
            enabled = password.isNotBlank() && !recoveryState.restoring,
            modifier = Modifier.fillMaxWidth()
        ) { Text("بازیابی از نسخه پشتیبان رمزگذاری‌شده") }
        if (recoveryState.restoring) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("اعتبارسنجی و بازیابی اتمیک اطلاعات…")
        }
        recoveryState.error?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
    }
}

private fun periodKindLabel(kind: String) = when (kind) {
    "MENSTRUATION" -> "عادت ماهانه"
    "POSTPARTUM" -> "نفاس"
    "ILLNESS" -> "بیماری"
    else -> "بازه ثبت‌شده توسط کاربر"
}
