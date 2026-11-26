package ir.hirmand.missedprayers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.hirmand.missedprayers.data.AppDatabase
import ir.hirmand.missedprayers.data.DebtRepository
import ir.hirmand.missedprayers.data.QuranContentRepository
import ir.hirmand.missedprayers.data.EncryptedBackupManager
import ir.hirmand.missedprayers.data.TanzilAssetTextProvider
import ir.hirmand.missedprayers.ui.DebtViewModel
import ir.hirmand.missedprayers.ui.HomeScreen
import ir.hirmand.missedprayers.ui.DatabaseUnavailableScreen
import ir.hirmand.missedprayers.ui.DatabaseLoadingScreen
import ir.hirmand.missedprayers.ui.theme.MissedWorshipTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: DebtViewModel by viewModels {
        val repository = DebtRepository(AppDatabase.get(applicationContext))
        val quranRepository = QuranContentRepository(AppDatabase.get(applicationContext).debtDao(), TanzilAssetTextProvider(assets))
        val backupManager = EncryptedBackupManager(AppDatabase.get(applicationContext))
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DebtViewModel(repository, quranRepository, backupManager) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("sqlcipher")
        setContent {
            var databaseChecked by remember { mutableStateOf(false) }
            var databaseFailure by remember { mutableStateOf<Throwable?>(null) }
            LaunchedEffect(Unit) {
                databaseFailure = withContext(Dispatchers.IO) {
                    runCatching {
                        // Room opens lazily; building the singleton alone does not prove the
                        // SQLCipher key, database header or migrations can actually be opened.
                        AppDatabase.get(applicationContext).openHelper.writableDatabase
                        Unit
                    }.exceptionOrNull()
                }
                databaseChecked = true
            }
            val keyMaterialMissing = databaseFailure?.message?.contains("کلید") == true
            MissedWorshipTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        !databaseChecked -> DatabaseLoadingScreen()
                        databaseFailure != null -> DatabaseUnavailableScreen(
                            keyMaterialMissing = keyMaterialMissing,
                            onRestoreBackup = { bytes, password ->
                                AppDatabase.restoreFromBackup(applicationContext, bytes, password)
                            },
                            onRecoveryComplete = { recreate() }
                        )
                        else -> HomeScreen(viewModel)
                    }
                }
            }
        }
    }
}
