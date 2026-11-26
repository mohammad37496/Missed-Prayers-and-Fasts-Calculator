package ir.hirmand.missedprayers.ui

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import ir.hirmand.missedprayers.ui.theme.MissedWorshipTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class RtlLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun applicationThemeProvidesRightToLeftLayoutDirection() {
        var actualDirection: LayoutDirection? = null
        composeRule.setContent {
            MissedWorshipTheme {
                actualDirection = LocalLayoutDirection.current
            }
        }
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, actualDirection)
    }
}
