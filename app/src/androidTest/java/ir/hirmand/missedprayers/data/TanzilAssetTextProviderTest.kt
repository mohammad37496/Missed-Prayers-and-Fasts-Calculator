package ir.hirmand.missedprayers.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TanzilAssetTextProviderTest {
    @Test fun bundledTanzilTextHasKnownOpeningAndAllSurahs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = TanzilAssetTextProvider(context.assets)
        assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", provider.getAyah(1, 1))
        assertNotNull(provider.getAyah(114, 6))
        assertNull(provider.getAyah(115, 1))
    }
}
