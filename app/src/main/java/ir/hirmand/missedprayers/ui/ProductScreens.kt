package ir.hirmand.missedprayers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import ir.hirmand.missedprayers.domain.DebtEstimateEngine
import ir.hirmand.missedprayers.domain.FiqhEvaluationContext
import ir.hirmand.missedprayers.domain.RecordedPeriod
import ir.hirmand.missedprayers.domain.PlanProjection
import ir.hirmand.missedprayers.domain.estimatedActiveDays
import ir.hirmand.missedprayers.domain.PrayerType
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfileEntity?, periods: List<ExcusePeriodEntity>,
    onSave: (UserProfileEntity) -> Unit,
    onAddPeriod: (String, LocalDate, LocalDate, String?) -> Unit,
    onDeletePeriod: (Long) -> Unit,
    onApplyEstimate: (Int, Int) -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile?.displayName.orEmpty()) }
    var gender by remember(profile) { mutableStateOf(profile?.gender ?: "UNSPECIFIED") }
    var start by remember(profile) { mutableStateOf(profile?.taklifStartDate.orEmpty()) }
    var end by remember(profile) { mutableStateOf(profile?.taklifEndDate.orEmpty()) }
    var calendar by remember(profile) { mutableStateOf(profile?.calendarPreference ?: "PERSIAN") }
    var timezoneId by remember(profile) { mutableStateOf(profile?.timezoneId ?: ZoneId.systemDefault().id) }
    var periodKind by remember { mutableStateOf("MENSTRUATION") }
    var periodStart by remember { mutableStateOf("") }
    var periodEnd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val estimatePreview = runCatching {
        if (start.isBlank()) null else {
            val from = LocalDate.parse(start)
            val to = end.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: CalendarDates.today(timezoneId)
            DebtEstimateEngine.estimate(from, to, FiqhEvaluationContext(
                gender,
                periods.map { RecordedPeriod(it.kind, LocalDate.parse(it.startDate), LocalDate.parse(it.endDate)) }
            ))
        }
    }.getOrNull()
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
        item { OutlinedTextField(start, { start = it }, label = { Text("شروع بازه برآورد (میلادی YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(end, { end = it }, label = { Text("پایان بازه برآورد (اختیاری، YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(timezoneId, { timezoneId = it.take(80) }, label = { Text("منطقه زمانی IANA (مثلاً Asia/Tehran)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("تقویم پیش‌فرض") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("PERSIAN" to "شمسی", "ISLAMIC_CIVIL" to "قمری محاسباتی", "GREGORIAN" to "میلادی").forEach { (value, label) ->
                    OutlinedButton(onClick = { calendar = value }) { Text(if (calendar == value) "✓ $label" else label) }
                }
            }
        }
        item {
            Button(onClick = {
                runCatching {
                    start.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                    end.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                    ZoneId.of(timezoneId)
                    onSave(UserProfileEntity(displayName = name.ifBlank { null }, gender = gender, taklifStartDate = start.ifBlank { null }, taklifEndDate = end.ifBlank { null }, timezoneId = timezoneId, calendarPreference = calendar, marjaId = profile?.marjaId, updatedAt = System.currentTimeMillis()))
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
                        onSave(UserProfileEntity(displayName = name.ifBlank { null }, gender = gender, taklifStartDate = start.ifBlank { null }, taklifEndDate = end.ifBlank { null }, timezoneId = timezoneId, calendarPreference = calendar, marjaId = profile?.marjaId, updatedAt = System.currentTimeMillis()))
                        onApplyEstimate(rawPrayerEstimate, rawFastingEstimate)
                    }, modifier = Modifier.fillMaxWidth()) { Text("اعمال برآورد خام و حفظ سوابق") }
                }
            }
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
                    OutlinedTextField(periodStart, { periodStart = it }, label = { Text("شروع YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(periodEnd, { periodEnd = it }, label = { Text("پایان YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        runCatching { onAddPeriod(periodKind, LocalDate.parse(periodStart), LocalDate.parse(periodEnd), null); periodStart = ""; periodEnd = "" }
                            .onFailure { error = "بازه تاریخ معتبر نیست یا پایان قبل از شروع است." }
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
                        Text("${period.startDate} تا ${period.endDate}", style = MaterialTheme.typography.bodySmall)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mask and (1 shl index) != 0, onCheckedChange = { checked -> mask = if (checked) mask or (1 shl index) else mask and (1 shl index).inv() })
                        Text(label)
                    }
                }
            }
        }
        item { if (projection != null) Text("با $remaining نماز باقی‌مانده، حدود ${projection.estimatedActiveDays()} روز فعال و پایان تقریبی ${CalendarDates.display(projection.estimatedCompletionDate(CalendarDates.today(timezoneId ?: ZoneId.systemDefault().id)))} لازم است.") }
        item { Text("روزه باقی‌مانده: $fastingRemaining روز؛ با هدف ${fastingTarget.toIntOrNull() ?: 0} در روز فعال، زمان تقریبی ${estimatedActiveDays(fastingRemaining.toLong(), (fastingTarget.toIntOrNull() ?: 1).coerceAtLeast(1))} روز فعال است.") }
        item { Text("یادآورها با WorkManager زمان‌بندی می‌شوند و ممکن است به‌دلیل حالت ذخیره باتری یا محدودیت سیستم‌عامل با تأخیر اجرا شوند.", style = MaterialTheme.typography.bodySmall) }
        item {
            if (reminders && notificationPermissionGranted) {
                Text("مجوز اعلان فعال است؛ یادآور طبق برنامه زمان‌بندی می‌شود.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            } else if (reminders) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مجوز اعلان غیرفعال است؛ تا فعال‌کردن مجوز در تنظیمات دستگاه، اعلانی نمایش داده نمی‌شود.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) { Text("تنظیم مجوز اعلان") }
                }
            } else {
                Text("یادآورها خاموش هستند.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = reminders, onCheckedChange = { reminders = it })
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
    val today = CalendarDates.today(zone)
    val todayPersian = CalendarDates.toPersian(today)
    val todayIslamic = CalendarDates.toIslamicCivil(today)
    var shownMonth by remember(todayPersian.year, todayPersian.month) { mutableStateOf(CalendarDate(todayPersian.year, todayPersian.month, 1)) }
    var selectedDate by remember(today) { mutableStateOf(today) }
    fun shiftMonth(delta: Int) {
        val absolute = shownMonth.year.toLong() * 12 + shownMonth.month - 1 + delta
        val year = Math.floorDiv(absolute, 12).toInt()
        val month = Math.floorMod(absolute, 12L).toInt() + 1
        val firstDay = CalendarDate(year, month, 1)
        shownMonth = firstDay
        selectedDate = CalendarDates.fromPersian(firstDay)
    }
    val monthStart = CalendarDates.fromPersian(shownMonth)
    val nextMonth = if (shownMonth.month == 12) CalendarDate(shownMonth.year + 1, 1, 1) else CalendarDate(shownMonth.year, shownMonth.month + 1, 1)
    val monthDays = ChronoUnit.DAYS.between(monthStart, CalendarDates.fromPersian(nextMonth)).toInt()
    val leading = (monthStart.dayOfWeek.value + 1) % 7 // Saturday is the first column.
    val selectedPersian = CalendarDates.toPersian(selectedDate)
    val selectedIslamic = CalendarDates.toIslamicCivil(selectedDate)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("تقویم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("امروز · ${today.format(DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.forLanguageTag("fa-IR")))}", color = MaterialTheme.colorScheme.onPrimary)
                Text("شمسی ${todayPersian.year}/${todayPersian.month}/${todayPersian.day}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                Text("قمری محاسباتی ${todayIslamic.year}/${todayIslamic.month}/${todayIslamic.day}", color = MaterialTheme.colorScheme.onPrimary)
                Text("منطقه زمانی: $zone", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { shiftMonth(-1) }) { Text("ماه قبل") }
            Text("${shownMonth.year} · ماه ${shownMonth.month}", fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { shiftMonth(1) }) { Text("ماه بعد") }
        }
        OutlinedButton(onClick = { shownMonth = CalendarDate(todayPersian.year, todayPersian.month, 1); selectedDate = today }) { Text("بازگشت به امروز") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { Text(it, fontWeight = FontWeight.Bold) }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(42) { index ->
                val day = index - leading + 1
                if (day in 1..monthDays) {
                    val persianDate = CalendarDate(shownMonth.year, shownMonth.month, day)
                    val gregorian = CalendarDates.fromPersian(persianDate)
                    val isSelected = gregorian == selectedDate
                    Card(onClick = { selectedDate = gregorian }, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
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
                Text("شمسی: ${selectedPersian.year}/${selectedPersian.month}/${selectedPersian.day}")
                Text("میلادی: $selectedDate")
                Text("قمری مدنی: ${selectedIslamic.year}/${selectedIslamic.month}/${selectedIslamic.day}")
            }
        }
        Text("تاریخ قمری بر اساس تقویم محاسباتی مدنی است و ممکن است با رؤیت هلال یا تقویم رسمی محل زندگی تفاوت داشته باشد.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ReportsScreen(prayers: List<PrayerDebtEntity>, fasting: FastingDebtEntity?, records: List<ir.hirmand.missedprayers.data.DebtRecordEntity>) {
    var search by remember { mutableStateOf("") }
    val prayerCompleted = prayers.sumOf { it.completed.toLong() }
    val prayerTotal = prayers.sumOf { it.total.toLong() }
    val fastingCompleted = fasting?.completed ?: 0
    val fastingTotal = fasting?.total ?: 0
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("گزارش پیشرفت", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        item { Text("نماز: $prayerCompleted از $prayerTotal انجام‌شده · ${ (prayerTotal - prayerCompleted).coerceAtLeast(0) } باقی‌مانده") }
        item { androidx.compose.material3.LinearProgressIndicator(progress = { if (prayerTotal == 0L) 0f else (prayerCompleted.toDouble() / prayerTotal).toFloat() }, modifier = Modifier.fillMaxWidth()) }
        item { Text("روزه: $fastingCompleted از $fastingTotal انجام‌شده · ${(fastingTotal - fastingCompleted).coerceAtLeast(0)} باقی‌مانده") }
        item { androidx.compose.material3.LinearProgressIndicator(progress = { if (fastingTotal == 0) 0f else fastingCompleted.toFloat() / fastingTotal }, modifier = Modifier.fillMaxWidth()) }
        items(prayers, key = { it.prayerType }) { debt ->
            val label = PrayerType.entries.firstOrNull { it.name == debt.prayerType }?.title ?: debt.prayerType
            Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("نماز $label")
                Text("${debt.completed} انجام‌شده · ${(debt.total - debt.completed).coerceAtLeast(0)} باقی‌مانده")
                androidx.compose.material3.LinearProgressIndicator(progress = { if (debt.total == 0) 0f else debt.completed.toFloat() / debt.total }, modifier = Modifier.fillMaxWidth())
            } }
        }
        item { OutlinedTextField(search, { search = it }, label = { Text("جست‌وجو در تاریخچه") }, modifier = Modifier.fillMaxWidth()) }
        items(records.filter { it.kind.contains(search, true) || it.reference.contains(search, true) || it.createdAt.toString().contains(search) }) { record ->
            val category = if (record.reference == "FASTING") "روزه" else "نماز ${PrayerType.entries.firstOrNull { it.name == record.reference }?.title ?: record.reference}"
            val kindLabel = if (record.kind.endsWith("ADJUSTMENT")) "اصلاح شمارنده" else "ثبت انجام‌شده"
            val localDate = java.time.Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            Card { Column(Modifier.padding(10.dp)) {
                Text("$category · $kindLabel", fontWeight = FontWeight.SemiBold)
                Text(if (record.kind.endsWith("ADJUSTMENT")) "${record.beforeCompleted} ← ${record.afterCompleted}" else "+${record.amount} مورد")
                Text(CalendarDates.display(localDate), style = MaterialTheme.typography.labelSmall)
            } }
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
        verse?.recitationUrl?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(PlayerAudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
        }
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
                Text("سوره ${verse.surah} · آیه ${verse.ayah}", style = MaterialTheme.typography.labelMedium)
                Text(verse.arabicText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(verse.persianTranslation, style = MaterialTheme.typography.bodyLarge)
                Text("ترجمه: ${verse.translationSource} · نسخه ${verse.translationVersion}", style = MaterialTheme.typography.labelSmall)
                if (verse.tafsirText != null) {
                    Text("تفسیر", fontWeight = FontWeight.Bold)
                    Text(verse.tafsirText)
                    Text("منبع تفسیر: ${verse.tafsirSource}", style = MaterialTheme.typography.labelSmall)
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
        item { OutlinedTextField(password, { password = it.take(128) }, label = { Text("گذرواژه قوی (حداقل ۱۲ نویسه)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Button(onClick = { val value = password; password = ""; onCreate(value) }, enabled = password.length >= 12, modifier = Modifier.fillMaxWidth()) { Text("ساخت فایل پشتیبان رمزگذاری‌شده") } }
        item { Text("بازیابی، اطلاعات فعلی برنامه را با اطلاعات فایل جایگزین می‌کند. ابتدا یک نسخه پشتیبان از اطلاعات فعلی بگیرید.", color = MaterialTheme.colorScheme.error) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = confirmReplace, onCheckedChange = { confirmReplace = it })
                Text("جایگزینی کامل داده‌های فعلی را می‌پذیرم")
            }
        }
        item { OutlinedButton(onClick = { val value = password; password = ""; onRestore(value) }, enabled = password.length >= 12 && confirmReplace, modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل و بازیابی") } }
        if (status != null) item { Text(status, color = MaterialTheme.colorScheme.secondary) }
        item { Text("فایل‌های ناسازگار، ناقص، دست‌کاری‌شده یا دارای گذرواژه اشتباه پیش از شروع تراکنش رد می‌شوند. بازیابی فقط پس از اعتبارسنجی کامل و در یک تراکنش دیتابیس انجام می‌شود.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun MoreScreen(onProfile: () -> Unit, onFiqh: () -> Unit, onBackup: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("بخش‌های بیشتر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("پروفایل و اطلاعات برآورد") }
        Button(onClick = onFiqh, modifier = Modifier.fillMaxWidth()) { Text("تنظیمات فقهی و مدیریت داده‌ها") }
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("پشتیبان‌گیری و بازیابی امن") }
    }
}

private fun periodKindLabel(kind: String) = when (kind) {
    "MENSTRUATION" -> "عادت ماهانه"
    "POSTPARTUM" -> "نفاس"
    "ILLNESS" -> "بیماری"
    else -> "بازه ثبت‌شده توسط کاربر"
}
