package ir.hirmand.missedprayers.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import ir.hirmand.missedprayers.data.DebtRepository
import ir.hirmand.missedprayers.domain.PrayerType
import ir.hirmand.missedprayers.domain.RecordingGuard
import ir.hirmand.missedprayers.data.UserProfileEntity
import ir.hirmand.missedprayers.data.PlannerSettingsEntity
import ir.hirmand.missedprayers.data.FiqhPreferencesEntity
import ir.hirmand.missedprayers.data.QuranContentRepository
import ir.hirmand.missedprayers.data.DailyVerseResult
import ir.hirmand.missedprayers.data.EncryptedBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DebtViewModel(private val repository: DebtRepository, private val quranRepository: QuranContentRepository, private val backupManager: EncryptedBackupManager) : ViewModel() {
    private val recordingGuard = RecordingGuard()
    private var pendingBackupForPicker: ByteArray? = null
    private var pendingRestorePasswordForPicker: CharArray? = null
    private val _dailyVerse = MutableStateFlow(DailyVerseResult(null, false, null))
    val dailyVerse = _dailyVerse.asStateFlow()
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus = _backupStatus.asStateFlow()
    private val profileLoaded = MutableStateFlow(false)
    private val prayersLoaded = MutableStateFlow(false)
    private val fastingLoaded = MutableStateFlow(false)
    private val recentRecordsLoaded = MutableStateFlow(false)
    private val allRecordsLoaded = MutableStateFlow(false)
    private val excusePeriodsLoaded = MutableStateFlow(false)
    private val plannerSettingsLoaded = MutableStateFlow(false)
    private val fiqhPreferencesLoaded = MutableStateFlow(false)
    val localDataLoaded = combine(
        profileLoaded, prayersLoaded, fastingLoaded, recentRecordsLoaded,
        allRecordsLoaded, excusePeriodsLoaded, plannerSettingsLoaded, fiqhPreferencesLoaded
    ) { loaded -> loaded.all { it } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val prayers = repository.prayers.onEach { prayersLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fasting = repository.fasting.onEach { fastingLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recentRecords = repository.recentRecords.onEach { recentRecordsLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allRecords = repository.allRecords.onEach { allRecordsLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile = repository.profile.onEach { profileLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val excusePeriods = repository.excusePeriods.onEach { excusePeriodsLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plannerSettings = repository.plannerSettings.onEach { plannerSettingsLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val fiqhPreferences = repository.fiqhPreferences.onEach { fiqhPreferencesLoaded.value = true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Keeps picker hand-off data across Activity recreation without placing secrets in saved state. */
    fun holdBackupForPicker(bytes: ByteArray) {
        pendingBackupForPicker?.fill(0)
        pendingBackupForPicker = bytes
    }

    fun takeBackupForPicker(): ByteArray? = pendingBackupForPicker.also { pendingBackupForPicker = null }

    fun holdRestorePasswordForPicker(password: CharArray) {
        pendingRestorePasswordForPicker?.fill('\u0000')
        pendingRestorePasswordForPicker = password
    }

    fun takeRestorePasswordForPicker(): CharArray? = pendingRestorePasswordForPicker.also { pendingRestorePasswordForPicker = null }

    fun setBackupStatus(message: String?) {
        _backupStatus.value = message
    }

    /** SAF I/O belongs to the ViewModel scope so Activity recreation cannot cancel a one-shot restore. */
    fun saveBackupToUri(contentResolver: ContentResolver, uri: Uri, bytes: ByteArray) = viewModelScope.launch(Dispatchers.IO) {
        val status = try {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("فایل خروجی باز نشد")
            "نسخه پشتیبان رمزگذاری‌شده ذخیره شد."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "ذخیره فایل انجام نشد: ${error.message ?: "خطای ناشناخته"}"
        } finally {
            bytes.fill(0)
        }
        _backupStatus.value = status
    }.also { job ->
        // The body may never start when the scope is already cancelled; this still wipes the hand-off bytes.
        job.invokeOnCompletion { bytes.fill(0) }
    }

    fun restoreBackupFromUri(contentResolver: ContentResolver, uri: Uri, password: CharArray) = viewModelScope.launch(Dispatchers.IO) {
        var bytes: ByteArray? = null
        try {
            bytes = contentResolver.openInputStream(uri)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= ir.hirmand.missedprayers.data.EncryptedBackupCodec.MAX_FILE_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("فایل انتخاب‌شده باز نشد")
            backupManager.restoreBackup(bytes, password)
            _backupStatus.value = "بازیابی کامل شد؛ همه داده‌ها در یک تراکنش جایگزین شدند."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _backupStatus.value = "بازیابی انجام نشد: ${error.message ?: "خطای ناشناخته"}"
        } finally {
            bytes?.fill(0)
            password.fill('\u0000')
        }
    }.also { job ->
        // The body may never start when the scope is already cancelled; this still wipes the hand-off password.
        job.invokeOnCompletion { password.fill('\u0000') }
    }

    suspend fun refreshDailyVerse(date: LocalDate) {
        val previous = _dailyVerse.value
        _dailyVerse.value = previous.copy(loading = true, message = null)

        // Load the local copy first so opening the app remains useful without a network.
        // If a cached verse exists, refresh it in the background and keep it visible
        // until the fresh response succeeds (or the repository returns the cache).
        val cachedOrFresh = quranRepository.loadFor(date, forceRefresh = false)
        _dailyVerse.value = cachedOrFresh.copy(loading = cachedOrFresh.fromCache)
        if (cachedOrFresh.fromCache) {
            _dailyVerse.value = quranRepository.loadFor(date, forceRefresh = true)
        }
    }

    fun savePrayer(type: PrayerType, total: Int, completed: Int, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repository.initializePrayer(type, total, completed) }
            .onFailure { onError(it.message ?: "ذخیره اطلاعات انجام نشد") }
    }

    fun saveFasting(total: Int, completed: Int, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repository.initializeFasting(total, completed) }
            .onFailure { onError(it.message ?: "ذخیره اطلاعات انجام نشد") }
    }

    fun adjustPrayerTotal(type: PrayerType, total: Int, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repository.adjustPrayerTotal(type, total) }
            .onFailure { onError(it.message ?: "اصلاح سقف بدهی نماز انجام نشد") }
    }

    fun adjustFastingTotal(total: Int, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repository.adjustFastingTotal(total) }
            .onFailure { onError(it.message ?: "اصلاح سقف بدهی روزه انجام نشد") }
    }

    fun recordPrayer(type: PrayerType, amount: Int = 1, onError: (String) -> Unit = {}) {
        val key = "prayer:${type.name}"
        if (!recordingGuard.begin(key, SystemClock.elapsedRealtime())) return
        viewModelScope.launch {
            var successful = false
            try {
                runCatching { repository.recordPrayer(type, amount) }
                    .onSuccess { successful = true }
                    .onFailure { if (it is CancellationException) throw it else onError(it.message ?: "ثبت نماز انجام نشد") }
            } finally {
                recordingGuard.finish(key, successful)
            }
        }
    }
    fun recordFasting(amount: Int = 1, onError: (String) -> Unit = {}) {
        val key = "fasting"
        if (!recordingGuard.begin(key, SystemClock.elapsedRealtime())) return
        viewModelScope.launch {
            var successful = false
            try {
                runCatching { repository.recordFasting(amount) }
                    .onSuccess { successful = true }
                    .onFailure { if (it is CancellationException) throw it else onError(it.message ?: "ثبت روزه انجام نشد") }
            } finally {
                recordingGuard.finish(key, successful)
            }
        }
    }
    fun undoRecord(id: Long, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.undoRecord(id) }.onFailure { onError(it.message ?: "بازگردانی انجام نشد") }
    }
    fun applyRawDateEstimate(prayerPerType: Int, fastingDays: Int, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.applyRawDateEstimate(prayerPerType, fastingDays) }
            .onFailure { onError(it.message ?: "اعمال برآورد انجام نشد") }
    }

    suspend fun saveProfileAndApplyRawDateEstimate(profile: UserProfileEntity, prayerPerType: Int, fastingDays: Int) {
        repository.saveProfileAndApplyRawDateEstimate(profile, prayerPerType, fastingDays)
    }

    fun saveProfile(profile: UserProfileEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.saveProfile(profile) }.onFailure { onError(it.message ?: "ذخیره پروفایل انجام نشد") }
    }

    suspend fun addExcusePeriod(kind: String, start: LocalDate, end: LocalDate, note: String?): Boolean {
        val key = "period:$kind:$start:$end"
        if (!recordingGuard.begin(key, SystemClock.elapsedRealtime())) return false
        var successful = false
        try {
            repository.addExcusePeriod(kind, start, end, note)
            successful = true
            return true
        } finally {
            recordingGuard.finish(key, successful)
        }
    }

    fun deleteExcusePeriod(id: Long, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.deleteExcusePeriod(id) }.onFailure { onError(it.message ?: "حذف بازه انجام نشد") }
    }

    fun savePlannerSettings(settings: PlannerSettingsEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.savePlannerSettings(settings) }.onFailure { onError(it.message ?: "ذخیره برنامه انجام نشد") }
    }

    fun saveFiqhPreferences(preferences: FiqhPreferencesEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.saveFiqhPreferences(preferences) }.onFailure { onError(it.message ?: "ذخیره تنظیمات فقهی انجام نشد") }
    }

    fun createBackup(password: CharArray, onResult: (Result<ByteArray>) -> Unit) = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        val result = runBackupOperation(password) { backupManager.createBackup(password) }
        onResult(result)
    }

    fun restoreBackup(file: ByteArray, password: CharArray, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
        val result = runBackupOperation(password) { backupManager.restoreBackup(file, password) }
        onResult(result)
    }

    fun deleteAllData(onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repository.deleteAllData() })
    }

    override fun onCleared() {
        pendingBackupForPicker?.fill(0)
        pendingBackupForPicker = null
        pendingRestorePasswordForPicker?.fill('\u0000')
        pendingRestorePasswordForPicker = null
    }
}
