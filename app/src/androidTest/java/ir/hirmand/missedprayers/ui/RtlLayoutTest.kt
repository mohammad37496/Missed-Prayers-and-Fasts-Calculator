package ir.hirmand.missedprayers.ui

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import ir.hirmand.missedprayers.data.DebtRecordEntity
import ir.hirmand.missedprayers.domain.CalendarDates
import ir.hirmand.missedprayers.ui.theme.MissedWorshipTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId

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

    @Test fun calendarDetailsRemainReachableThroughVerticalScrolling() {
        composeRule.setContent {
            MissedWorshipTheme { CalendarScreen(profile = null) }
        }

        val caveat = "تاریخ قمری بر اساس تقویم محاسباتی مدنی است و ممکن است با رؤیت هلال یا تقویم رسمی محل زندگی تفاوت داشته باشد."
        composeRule.onNodeWithText(caveat).performScrollTo().assertIsDisplayed()
    }

    @Test fun plannerExplainsWhenEveryWeekdayIsDisabled() {
        composeRule.setContent {
            MissedWorshipTheme {
                PlannerScreen(
                    settings = null,
                    prayers = listOf("FAJR" to 5),
                    fastingRemaining = 0,
                    timezoneId = "UTC",
                    calendarPreference = "PERSIAN",
                    notificationPermissionGranted = true,
                    onOpenNotificationSettings = {},
                    onSave = {}
                )
            }
        }

        listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")
            .forEach { composeRule.onNodeWithText(it).performClick() }

        composeRule.onNodeWithText("حداقل یک روز فعال در هفته انتخاب کنید.").assertExists()
        composeRule.onNodeWithText("ذخیره برنامه و یادآورها").performScrollTo().assertIsNotEnabled()
    }

    @Test fun profileChoiceButtonsExposeSelectedStateToAccessibility() {
        composeRule.setContent {
            MissedWorshipTheme {
                ProfileScreen(
                    profile = null,
                    periods = emptyList(),
                    onSave = {},
                    onAddPeriod = { _, _, _, _ -> true },
                    onDeletePeriod = {},
                    onApplyEstimate = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("✓ مشخص نشده").assertIsSelected()
        composeRule.onNodeWithText("میلادی").performScrollTo().performClick()
        composeRule.onNodeWithText("✓ میلادی").assertIsSelected()
        composeRule.onNodeWithText("آقا").performScrollTo().performClick()
        composeRule.onNodeWithText("✓ آقا").assertIsSelected()
        composeRule.onNodeWithText("نفاس").performScrollTo().performClick()
        composeRule.onNodeWithText("✓ نفاس").assertIsSelected()
    }

    @Test fun weeklyActivityChartExposesUnambiguousFullDatesToAccessibility() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val record = DebtRecordEntity(
            id = 1,
            kind = "PRAYER",
            reference = "FAJR",
            amount = 1,
            createdAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            beforeCompleted = 0,
            afterCompleted = 1
        )
        composeRule.setContent {
            MissedWorshipTheme {
                ReportsScreen(
                    prayers = emptyList(),
                    fasting = null,
                    records = listOf(record),
                    timezoneId = ZoneId.systemDefault().id,
                    onUndoRecord = {}
                )
            }
        }

        val expectedDescription = (6 downTo 0).joinToString("؛ ") { offset ->
            val date = today.minusDays(offset.toLong())
            "${date}: نماز ${if (date == today) 1 else 0}، روزه 0"
        }
        composeRule.onNodeWithContentDescription(expectedDescription).assertExists()
        val todayLabelX = composeRule.onNodeWithContentDescription(today.toString()).fetchSemanticsNode().boundsInRoot.center.x
        val oldestLabelX = composeRule.onNodeWithContentDescription(today.minusDays(6).toString()).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("RTL date labels must stay aligned with the chart's left-to-right timeline", todayLabelX > oldestLabelX)
    }

    @Test fun moreDestinationsRemainReachableThroughVerticalScrolling() {
        composeRule.setContent {
            MissedWorshipTheme {
                MoreScreen(onProfile = {}, onFiqh = {}, onBackup = {}, onHelp = {})
            }
        }

        composeRule.onNodeWithText("راهنما و پرسش‌های متداول").performScrollTo().assertIsDisplayed()
    }
}
