package ir.hirmand.missedprayers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.hirmand.missedprayers.data.FastingDebtEntity
import ir.hirmand.missedprayers.data.PrayerDebtEntity
import ir.hirmand.missedprayers.domain.PrayerType

@Composable
fun HomeScreen(viewModel: DebtViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val profile by viewModel.profile.collectAsState()
    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationPermissionGranted = hasNotificationPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
    }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportPassword by remember { mutableStateOf<CharArray?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.hirmand.mwbk")) { uri ->
        val bytes = pendingBackup
        pendingBackup = null
        if (uri != null && bytes != null) scope.launch(Dispatchers.IO) {
            val saved = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("فایل خروجی باز نشد") }
            withContext(Dispatchers.Main) { backupStatus = if (saved.isSuccess) "نسخه پشتیبان رمزگذاری‌شده ذخیره شد." else "ذخیره فایل انجام نشد: ${saved.exceptionOrNull()?.message}" }
        } else backupStatus = "ذخیره نسخه پشتیبان لغو شد."
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingImportPassword
        pendingImportPassword = null
        if (uri != null && password != null) scope.launch(Dispatchers.IO) {
            val read = runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: error("فایل انتخاب‌شده باز نشد")
                input.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= ir.hirmand.missedprayers.data.EncryptedBackupCodec.MAX_FILE_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
            withContext(Dispatchers.Main) {
                read.onSuccess { bytes ->
                    viewModel.restoreBackup(bytes, password) { result -> backupStatus = if (result.isSuccess) "بازیابی کامل شد؛ همه داده‌ها در یک تراکنش جایگزین شدند." else "بازیابی انجام نشد: ${result.exceptionOrNull()?.message}" }
                }.onFailure { password.fill('\u0000'); backupStatus = "خواندن فایل ناموفق بود: ${it.message}" }
            }
        } else {
            password?.fill('\u0000')
            backupStatus = "انتخاب فایل بازیابی لغو شد."
        }
    }
    val prayers by viewModel.prayers.collectAsState()
    val fasting by viewModel.fasting.collectAsState()
    val recentRecords by viewModel.recentRecords.collectAsState()
    val periods by viewModel.excusePeriods.collectAsState()
    val plannerSettings by viewModel.plannerSettings.collectAsState()
    val fiqhPreferences by viewModel.fiqhPreferences.collectAsState()
    val dailyVerse by viewModel.dailyVerse.collectAsState()
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
        if (showSetup) {
            SetupScreen(
                savedPrayers = prayers,
                savedFasting = fasting,
                onSavePrayer = { type, total, done -> viewModel.savePrayer(type, total, done) { error = it } },
                onSaveFasting = { total, done -> viewModel.saveFasting(total, done) { error = it } },
                onContinue = { showSetup = false },
                error = error
            )
        } else {
            Scaffold(bottomBar = {
                NavigationBar {
                    listOf("dashboard" to "خانه", "calendar" to "تقویم", "planner" to "برنامه", "reports" to "گزارش", "more" to "بیشتر").forEach { (tab, label) ->
                        NavigationBarItem(selected = selectedTab == tab, onClick = { selectedTab = tab }, icon = { Text(when (tab) { "dashboard" -> "⌂"; "calendar" -> "▦"; "planner" -> "◷"; "reports" -> "▤"; else -> "⋯" }) }, label = { Text(label) })
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    error?.let { message ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { error = null }) { Text("بستن") }
                        }
                    }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        "calendar" -> CalendarScreen(profile)
                        "planner" -> PlannerScreen(plannerSettings, prayers.map { it.prayerType to (it.total - it.completed).coerceAtLeast(0) }, ((fasting?.total ?: 0) - (fasting?.completed ?: 0)).coerceAtLeast(0), profile?.timezoneId, notificationPermissionGranted, onOpenNotificationSettings = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { settings ->
                            viewModel.savePlannerSettings(settings) { error = it }
                            if (settings.reminderEnabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        "reports" -> ReportsScreen(prayers, fasting, viewModel.allRecords.collectAsState().value)
                        "more" -> MoreScreen(onProfile = { selectedTab = "profile" }, onFiqh = { selectedTab = "settings" }, onBackup = { selectedTab = "backup" }, onHelp = { selectedTab = "help" })
                        "help" -> HelpScreen()
                        "profile" -> ProfileScreen(profile, periods, { value -> viewModel.saveProfile(value) { error = it } },
                            { kind, start, end, note -> viewModel.addExcusePeriod(kind, start, end, note) { error = it } }, viewModel::deleteExcusePeriod,
                            { prayerDays, fastingDays -> viewModel.applyRawDateEstimate(prayerDays, fastingDays) { error = it }; selectedTab = "dashboard" })
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
                                result.onSuccess { bytes -> pendingBackup = bytes; createBackupLauncher.launch("missed-worship-backup.mwbk") }
                                    .onFailure { backupStatus = "ساخت نسخه پشتیبان ناموفق بود: ${it.message}" }
                            } },
                            onRestore = { password -> pendingImportPassword = password.toCharArray(); importBackupLauncher.launch(arrayOf("application/vnd.hirmand.mwbk", "application/octet-stream", "*/*")) })
                        else -> Dashboard(prayers, fasting, onRecordPrayer = viewModel::recordPrayer,
                            recentRecords = recentRecords,
                            dailyVerse = dailyVerse,
                            onRecordFasting = viewModel::recordFasting,
                            onRefreshVerse = viewModel::refreshDailyVerse,
                            onUndoRecord = { id -> viewModel.undoRecord(id) { error = it } },
                            onEdit = { showSetup = true })
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
    onRecordPrayer: (PrayerType, Int) -> Unit, onRecordFasting: (Int) -> Unit,
    onRefreshVerse: () -> Unit,
    onUndoRecord: (Long) -> Unit, onEdit: () -> Unit
) {
    val prayerRemaining = prayers.sumOf { (it.total - it.completed).coerceAtLeast(0).toLong() }
    val fastRemaining = ((fasting?.total ?: 0) - (fasting?.completed ?: 0)).coerceAtLeast(0)
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("السلام علیک", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            Text("قدم‌به‌قدم تا آرامش", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("پیشرفت عبادات قضاشده", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مجموع نمازهای باقی‌مانده", color = androidx.compose.ui.graphics.Color.White)
                    Text(prayerRemaining.toString(), style = MaterialTheme.typography.displayMedium, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                    Text("روزۀ قضا: $fastRemaining روز", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        item { Text("پیگیری نمازها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(prayers, key = { it.prayerType }) { debt ->
            val type = PrayerType.valueOf(debt.prayerType)
            DebtCard(title = "نماز ${type.title}", total = debt.total, completed = debt.completed, onRecord = { amount -> onRecordPrayer(type, amount) })
        }
        if (fasting != null) item { DebtCard("روزه قضا", fasting.total, fasting.completed, onRecordFasting) }
        if (recentRecords.isNotEmpty()) {
            item { Text("آخرین ثبت‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(recentRecords, key = { it.id }) { record ->
                val title = if (record.reference == "FASTING") "روزه قضا" else "نماز ${PrayerType.valueOf(record.reference).title}"
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("$title · ${if (record.kind.endsWith("ADJUSTMENT")) "اصلاح شمارنده" else "${record.amount} مورد"}", fontWeight = FontWeight.Medium)
                            if (record.kind.endsWith("ADJUSTMENT")) Text("${record.beforeCompleted} ← ${record.afterCompleted}", style = MaterialTheme.typography.labelSmall)
                            Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(record.createdAt)), style = MaterialTheme.typography.labelSmall)
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
