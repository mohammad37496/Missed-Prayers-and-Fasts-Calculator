package ir.hirmand.missedprayers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import ir.hirmand.missedprayers.ui.theme.MissedWorshipTheme

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
            MissedWorshipTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen(viewModel)
                }
            }
        }
    }
}
