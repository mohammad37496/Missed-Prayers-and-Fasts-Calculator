package ir.hirmand.missedprayers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.hirmand.missedprayers.data.FastingDebtEntity
import ir.hirmand.missedprayers.data.PrayerDebtEntity
import ir.hirmand.missedprayers.domain.PrayerType
import ir.hirmand.missedprayers.domain.CalendarDates

@Composable
fun HomeScreen(viewModel: DebtViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val profile by viewModel.profile.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile?.timezoneId) {
        val zone = runCatching { ZoneId.of(profile?.timezoneId ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault())
        while (true) {
            val verseDate = CalendarDates.today(zone.id)
            viewModel.refreshDailyVerse(verseDate)
            val nextLocalMidnight = verseDate.plusDays(1).atStartOfDay(zone)
            delay(Duration.between(ZonedDateTime.now(zone).toInstant(), nextLocalMidnight.toInstant()).toMillis().coerceAtLeast(1L))
        }
    }
    fun hasNotificationPermission(): Boolean {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission()) }
    DisposableEffect(lifecycleOwner, profile?.timezoneId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionGranted = hasNotificationPermission()
                scope.launch { viewModel.refreshDailyVerse(CalendarDates.today(profile?.timezoneId ?: ZoneId.systemDefault().id)) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
    }
    val backupStatus by viewModel.backupStatus.collectAsState()
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.hirmand.mwbk")) { uri ->
        val bytes = viewModel.takeBackupForPicker()
        if (uri != null && bytes != null) {
            viewModel.saveBackupToUri(context.contentResolver, uri, bytes)
        } else {
            bytes?.fill(0)
            viewModel.setBackupStatus(if (uri == null) "ذخیره نسخه پشتیبان لغو شد." else "اطلاعات موقت نسخه پشتیبان در دسترس نیست؛ دوباره فایل را بسازید.")
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = viewModel.takeRestorePasswordForPicker()
        if (uri != null && password != null) {
            viewModel.restoreBackupFromUri(context.contentResolver, uri, password)
        } else {
            password?.fill('\u0000')
            viewModel.setBackupStatus(if (uri == null) "انتخاب فایل بازیابی لغو شد." else "گذرواژه موقت در دسترس نیست؛ بازیابی را دوباره آغاز کنید.")
        }
    }
    val prayers by viewModel.prayers.collectAsState()
    val fasting by viewModel.fasting.collectAsState()
    val recentRecords by viewModel.recentRecords.collectAsState()
    val allRecords by viewModel.allRecords.collectAsState()
    val periods by viewModel.excusePeriods.collectAsState()
    val plannerSettings by viewModel.plannerSettings.collectAsState()
    val fiqhPreferences by viewModel.fiqhPreferences.collectAsState()
    val dailyVerse by viewModel.dailyVerse.collectAsState()
    val localDataLoaded by viewModel.localDataLoaded.collectAsState()
    var showSetup by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember(profile) { mutableStateOf(if (profile == null) "profile" else "dashboard") }

    LaunchedEffect(plannerSettings, profile?.timezoneId, notificationPermissionGranted) {
        plannerSettings?.let { settings ->
            if (settings.reminderEnabled && notificationPermissionGranted) {
                ir.hirmand.missedprayers.notifications.ReminderScheduler.schedule(context, settings, profile?.timezoneId ?: ZoneId.systemDefault().id)
            } else {
                ir.hirmand.missedprayers.notifications.ReminderScheduler.cancel(context)
            }
        }
    }

    CompositionDirectionRtl {
        if (!localDataLoaded) {
            DatabaseLoadingScreen()
        } else if (showSetup) {
            SetupScreen(
                savedPrayers = prayers,
                savedFasting = fasting,
                onSavePrayer = { type, total, done -> viewModel.savePrayer(type, total, done) { error = it } },
                onSaveFasting = { total, done -> viewModel.saveFasting(total, done) { error = it } },
                onContinue = { showSetup = false },
                error = error
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expandedLayout = maxWidth >= 600.dp
                val destinations = listOf("dashboard" to "خانه", "calendar" to "تقویم", "planner" to "برنامه", "reports" to "گزارش", "more" to "بیشتر")
                Scaffold(bottomBar = {
                    if (!expandedLayout) NavigationBar {
                        destinations.forEach { (tab, label) ->
                            NavigationBarItem(selected = selectedTab == tab, onClick = { selectedTab = tab }, icon = { Text(when (tab) { "dashboard" -> "⌂"; "calendar" -> "▦"; "planner" -> "◷"; "reports" -> "▤"; else -> "⋯" }) }, label = { Text(label) })
                        }
                    }
                }) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        if (expandedLayout) NavigationRail {
                            destinations.forEach { (tab, label) ->
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Text(when (tab) { "dashboard" -> "⌂"; "calendar" -> "▦"; "planner" -> "◷"; "reports" -> "▤"; else -> "⋯" }) },
                                    label = { Text(label) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            error?.let { message ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { error = null }) { Text("بستن") }
                                }
                            }
                            androidx.compose.foundation.layout.Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                                    when (selectedTab) {
                        "calendar" -> CalendarScreen(profile)
                        "planner" -> PlannerScreen(plannerSettings, prayers.map { it.prayerType to (it.total - it.completed).coerceAtLeast(0) }, ((fasting?.total ?: 0) - (fasting?.completed ?: 0)).coerceAtLeast(0), profile?.timezoneId, profile?.calendarPreference, notificationPermissionGranted, onOpenNotificationSettings = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { settings ->
                            viewModel.savePlannerSettings(settings) { error = it }
                            if (settings.reminderEnabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        "reports" -> ReportsScreen(
                            prayers,
                            fasting,
                            allRecords,
                            profile?.calendarPreference ?: "PERSIAN",
                            profile?.timezoneId,
                            onUndoRecord = { id -> viewModel.undoRecord(id) { error = it } }
                        )
                        "more" -> MoreScreen(onProfile = { selectedTab = "profile" }, onFiqh = { selectedTab = "settings" }, onBackup = { selectedTab = "backup" }, onHelp = { selectedTab = "help" })
                        "help" -> HelpScreen()
                        "profile" -> ProfileScreen(profile, periods, { value -> viewModel.saveProfile(value) { error = it } },
                            { kind, start, end, note -> viewModel.addExcusePeriod(kind, start, end, note) },
                            { id -> viewModel.deleteExcusePeriod(id) { error = it } },
                            { value, prayerDays, fastingDays ->
                                viewModel.saveProfileAndApplyRawDateEstimate(value, prayerDays, fastingDays)
                                selectedTab = "dashboard"
                            })
                        "settings" -> SettingsScreen(fiqhPreferences, { viewModel.saveFiqhPreferences(it) { error = it } }) {
                            viewModel.deleteAllData { result ->
                                if (result.isSuccess) {
                                    ir.hirmand.missedprayers.notifications.ReminderScheduler.cancel(context)
                                    selectedTab = "profile"
                                    error = null
                                } else error = "حذف اطلاعات انجام نشد: ${result.exceptionOrNull()?.message}"
                            }
                        }
                        "backup" -> BackupScreen(backupStatus,
                            onCreate = { password -> viewModel.createBackup(password.toCharArray()) { result ->
                                result.onSuccess { bytes -> viewModel.holdBackupForPicker(bytes); createBackupLauncher.launch("missed-worship-backup.mwbk") }
                                    .onFailure { viewModel.setBackupStatus("ساخت نسخه پشتیبان ناموفق بود: ${it.message}") }
                            } },
                            onRestore = { password -> viewModel.holdRestorePasswordForPicker(password.toCharArray()); importBackupLauncher.launch(arrayOf("application/vnd.hirmand.mwbk", "application/octet-stream", "*/*")) })
                        else -> Dashboard(prayers, fasting, onRecordPrayer = { type, amount -> viewModel.recordPrayer(type, amount) { error = it } },
                            recentRecords = recentRecords,
                            dailyVerse = dailyVerse,
                            timezoneId = profile?.timezoneId,
                            calendarPreference = profile?.calendarPreference ?: "PERSIAN",
                            onRecordFasting = { amount -> viewModel.recordFasting(amount) { error = it } },
                            onRefreshVerse = { scope.launch { viewModel.refreshDailyVerse(CalendarDates.today(profile?.timezoneId ?: ZoneId.systemDefault().id)) } },
                            onUndoRecord = { id -> viewModel.undoRecord(id) { error = it } },
                            onEdit = { showSetup = true })
                    }
                                }
                            }
                        }
                }
                }
            }
        }
    }
}

@Composable
private fun CompositionDirectionRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
        content = content
    )
}

@Composable
private fun SetupScreen(
    savedPrayers: List<PrayerDebtEntity>,
    savedFasting: FastingDebtEntity?,
    onSavePrayer: (PrayerType, Int, Int) -> Unit,
    onSaveFasting: (Int, Int) -> Unit,
    onContinue: () -> Unit,
    error: String?
) {
    var selectedPrayer by remember { mutableStateOf(PrayerType.FAJR) }
    val selectedDebt = savedPrayers.firstOrNull { it.prayerType == selectedPrayer.name }
    var prayerTotal by remember(selectedPrayer, savedPrayers) { mutableStateOf(selectedDebt?.total?.toString() ?: "0") }
    var prayerDone by remember(selectedPrayer, savedPrayers) { mutableStateOf(selectedDebt?.completed?.toString() ?: "0") }
    var fastTotal by remember(savedFasting) { mutableStateOf(savedFasting?.total?.toString() ?: "0") }
    var fastDone by remember(savedFasting) { mutableStateOf(savedFasting?.completed?.toString() ?: "0") }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("آغاز مسیر جبران", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("مقادیر را بر اساس برآورد و بررسی شخصی خود وارد کنید. این برنامه به‌جای شما حکم شرعی تعیین نمی‌کند.", style = MaterialTheme.typography.bodyMedium)
        }
        item { Text("نمازهای قضا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrayerType.entries.forEach { type ->
                    OutlinedButton(onClick = { selectedPrayer = type }) { Text(type.title) }
                }
            }
        }
        item { NumberField("تعداد کل نماز ${selectedPrayer.title}", prayerTotal) { prayerTotal = it } }
        item { NumberField("تعداد انجام‌شده قبلی", prayerDone) { prayerDone = it } }
        item { Button(onClick = { onSavePrayer(selectedPrayer, prayerTotal.toIntOrNull() ?: -1, prayerDone.toIntOrNull() ?: -1) }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره این نماز") } }
        item { Text("روزه‌های قضا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        item { NumberField("تعداد کل روزه", fastTotal) { fastTotal = it } }
        item { NumberField("تعداد روزه انجام‌شده قبلی", fastDone) { fastDone = it } }
        item { Button(onClick = { onSaveFasting(fastTotal.toIntOrNull() ?: -1, fastDone.toIntOrNull() ?: -1) }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره روزه‌ها") } }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("ورود به داشبورد") } }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onValueChange(it.filter(Char::isDigit).take(7)) },
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun Dashboard(
    prayers: List<PrayerDebtEntity>, fasting: FastingDebtEntity?,
    recentRecords: List<ir.hirmand.missedprayers.data.DebtRecordEntity>,
    dailyVerse: ir.hirmand.missedprayers.data.DailyVerseResult,
    timezoneId: String?, calendarPreference: String,
    onRecordPrayer: (PrayerType, Int) -> Unit, onRecordFasting: (Int) -> Unit,
    onRefreshVerse: () -> Unit,
    onUndoRecord: (Long) -> Unit, onEdit: () -> Unit
) {
    val zone = remember(timezoneId) { runCatching { ZoneId.of(timezoneId ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault()) }
    val prayerRemaining = prayers.sumOf { (it.total - it.completed).coerceAtLeast(0).toLong() }
    val fastRemaining = ((fasting?.total ?: 0) - (fasting?.completed ?: 0)).coerceAtLeast(0)
    val hasDebt = prayers.isNotEmpty() || fasting != null
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("السلام علیک", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text("قدم‌به‌قدم تا آرامش", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("پیشرفت عبادات قضاشده", style = MaterialTheme.typography.bodyMedium)
        }
        if (hasDebt) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مجموع نمازهای باقی‌مانده", color = androidx.compose.ui.graphics.Color.White)
                    Text(prayerRemaining.toString(), style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                    Text("روزۀ قضا: $fastRemaining روز", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        if (!hasDebt) item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هنوز برآوردی ثبت نشده است", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("برای شروع، تعداد تقریبی نمازها و روزه‌های قضا را وارد کنید. می‌توانید بعداً آن‌ها را بدون حذف تاریخچه اصلاح کنید.")
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("ثبت برآورد اولیه") }
                }
            }
        } else {
            item { Text("پیگیری نمازها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(prayers, key = { it.prayerType }) { debt ->
                val type = PrayerType.valueOf(debt.prayerType)
                DebtCard(title = "نماز ${type.title}", total = debt.total, completed = debt.completed, onRecord = { amount -> onRecordPrayer(type, amount) })
            }
            if (fasting != null) item { DebtCard("روزه قضا", fasting.total, fasting.completed, onRecordFasting) }
        }
        if (recentRecords.isNotEmpty()) {
            item { Text("آخرین ثبت‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(recentRecords, key = { it.id }) { record ->
                val title = if (record.reference == "FASTING") "روزه قضا" else "نماز ${PrayerType.valueOf(record.reference).title}"
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("$title · ${if (record.kind.endsWith("ADJUSTMENT")) "اصلاح شمارنده" else "${record.amount} مورد"}", fontWeight = FontWeight.Medium)
                            if (record.kind.endsWith("ADJUSTMENT")) Text("${record.beforeCompleted} ← ${record.afterCompleted}", style = MaterialTheme.typography.labelSmall)
                            val localDateTime = java.time.Instant.ofEpochMilli(record.createdAt).atZone(zone)
                            Text("${CalendarDates.displayPreferred(localDateTime.toLocalDate(), calendarPreference)} · ${localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { onUndoRecord(record.id) }) { Text("بازگردانی") }
                    }
                }
            }
        }
        item { DailyVerseCard(dailyVerse, onRefreshVerse) }
        item { HadithSourceStatusCard() }
        item { OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("ویرایش برآورد اولیه") } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DebtCard(title: String, total: Int, completed: Int, onRecord: (Int) -> Unit) {
    var amount by remember(title) { mutableStateOf("1") }
    val remaining = (total - completed).coerceAtLeast(0)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$remaining باقی‌مانده", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { if (total == 0) 0f else completed.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text("$completed از $total انجام‌شده", style = MaterialTheme.typography.bodySmall)
            NumberField("تعداد برای ثبت گروهی", amount) { amount = it }
            Button(onClick = { amount.toIntOrNull()?.let(onRecord) }, enabled = remaining > 0 && amount.toIntOrNull() in 1..10_000, modifier = Modifier.fillMaxWidth()) { Text("ثبت انجام‌شده") }
        }
    }
}
