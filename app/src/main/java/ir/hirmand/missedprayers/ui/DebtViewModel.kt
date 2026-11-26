package ir.hirmand.missedprayers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.hirmand.missedprayers.data.DebtRepository
import ir.hirmand.missedprayers.domain.PrayerType
import ir.hirmand.missedprayers.data.UserProfileEntity
import ir.hirmand.missedprayers.data.PlannerSettingsEntity
import ir.hirmand.missedprayers.data.FiqhPreferencesEntity
import ir.hirmand.missedprayers.data.QuranContentRepository
import ir.hirmand.missedprayers.data.DailyVerseResult
import ir.hirmand.missedprayers.data.EncryptedBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebtViewModel(private val repository: DebtRepository, private val quranRepository: QuranContentRepository, private val backupManager: EncryptedBackupManager) : ViewModel() {
    private val _dailyVerse = MutableStateFlow(DailyVerseResult(null, false, null))
    val dailyVerse = _dailyVerse.asStateFlow()
    val prayers = repository.prayers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fasting = repository.fasting.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recentRecords = repository.recentRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allRecords = repository.allRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile = repository.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val excusePeriods = repository.excusePeriods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plannerSettings = repository.plannerSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val fiqhPreferences = repository.fiqhPreferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { refreshDailyVerse() }

    fun refreshDailyVerse() = viewModelScope.launch {
        val previous = _dailyVerse.value
        _dailyVerse.value = previous.copy(loading = true, message = null)

        // Load the local copy first so opening the app remains useful without a network.
        // If a cached verse exists, refresh it in the background and keep it visible
        // until the fresh response succeeds (or the repository returns the cache).
        val cachedOrFresh = quranRepository.loadFor(forceRefresh = false)
        _dailyVerse.value = cachedOrFresh.copy(loading = cachedOrFresh.fromCache)
        if (cachedOrFresh.fromCache) {
            _dailyVerse.value = quranRepository.loadFor(forceRefresh = true)
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

    fun recordPrayer(type: PrayerType, amount: Int = 1) = viewModelScope.launch { repository.recordPrayer(type, amount) }
    fun recordFasting(amount: Int = 1) = viewModelScope.launch { repository.recordFasting(amount) }
    fun undoRecord(id: Long, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.undoRecord(id) }.onFailure { onError(it.message ?: "بازگردانی انجام نشد") }
    }
    fun applyRawDateEstimate(prayerPerType: Int, fastingDays: Int, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.applyRawDateEstimate(prayerPerType, fastingDays) }
            .onFailure { onError(it.message ?: "اعمال برآورد انجام نشد") }
    }

    fun saveProfile(profile: UserProfileEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.saveProfile(profile) }.onFailure { onError(it.message ?: "ذخیره پروفایل انجام نشد") }
    }

    fun addExcusePeriod(kind: String, start: LocalDate, end: LocalDate, note: String?, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.addExcusePeriod(kind, start, end, note) }.onFailure { onError(it.message ?: "ثبت بازه انجام نشد") }
    }

    fun deleteExcusePeriod(id: Long) = viewModelScope.launch { repository.deleteExcusePeriod(id) }

    fun savePlannerSettings(settings: PlannerSettingsEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.savePlannerSettings(settings) }.onFailure { onError(it.message ?: "ذخیره برنامه انجام نشد") }
    }

    fun saveFiqhPreferences(preferences: FiqhPreferencesEntity, onError: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.saveFiqhPreferences(preferences) }.onFailure { onError(it.message ?: "ذخیره تنظیمات فقهی انجام نشد") }
    }

    fun createBackup(password: CharArray, onResult: (Result<ByteArray>) -> Unit) = viewModelScope.launch {
        val result = runCatching { backupManager.createBackup(password) }
        password.fill('\u0000')
        onResult(result)
    }

    fun restoreBackup(file: ByteArray, password: CharArray, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        val result = runCatching { backupManager.restoreBackup(file, password) }
        password.fill('\u0000')
        onResult(result)
    }

    fun deleteAllData(onResult: (Result<Unit>) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repository.deleteAllData() })
    }
}
