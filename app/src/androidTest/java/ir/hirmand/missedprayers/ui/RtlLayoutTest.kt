package ir.hirmand.missedprayers.ui

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import ir.hirmand.missedprayers.domain.CalendarDates
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

    @Test fun calendarExposesSelectedDayAndUpdatesItsAccessibilityState() {
        val today = CalendarDates.today()
        val preferred = CalendarDates.toPersian(today)
        val otherDay = if (preferred.day == 1) 2 else 1
        composeRule.setContent {
            MissedWorshipTheme { CalendarScreen(profile = null) }
        }

        composeRule.onNodeWithText(preferred.day.toString()).assertIsSelected()
        composeRule.onNodeWithText(otherDay.toString()).performClick()
        composeRule.onNodeWithText(otherDay.toString()).assertIsSelected()
    }
}
