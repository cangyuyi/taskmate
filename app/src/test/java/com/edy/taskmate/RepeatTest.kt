package com.edy.taskmate

import com.edy.taskmate.data.Repeat
import com.edy.taskmate.data.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RepeatTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun parts(ts: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return Triple(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    @Test
    fun dailyAddsOneDayKeepingClock() {
        val next = Repeat.next(at(2026, 10, 28), RepeatRule.DAILY)
        assertEquals(Triple(2026, 10, 29), parts(next))
        val c = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(9, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun weeklyAddsSevenDays() {
        assertEquals(Triple(2026, 11, 4), parts(Repeat.next(at(2026, 10, 28), RepeatRule.WEEKLY)))
    }

    @Test
    fun weekdaysSkipsWeekend() {
        // 2026-10-30 是周五，下一次工作日应该是 11-02 周一
        assertEquals(Triple(2026, 11, 2), parts(Repeat.next(at(2026, 10, 30), RepeatRule.WEEKDAYS)))
    }

    @Test
    fun monthlyClampsToMonthEnd() {
        // 2026 不是闰年，1 月 31 日推到 2 月 28 日
        assertEquals(Triple(2026, 2, 28), parts(Repeat.next(at(2026, 1, 31), RepeatRule.MONTHLY)))
    }

    @Test
    fun noneReturnsSameMoment() {
        val ts = at(2026, 10, 28)
        assertEquals(ts, Repeat.next(ts, RepeatRule.NONE))
    }

    @Test
    fun nextFutureTimeSkipsMissedOccurrences() {
        val now = at(2026, 10, 28, 12)
        val next = Repeat.nextFutureTime(at(2026, 10, 1), RepeatRule.DAILY, now)
        assertTrue(next > now)
        assertTrue(next - now <= 24 * 60 * 60 * 1000L)
    }

    @Test
    fun unknownRuleFallsBackToNone() {
        assertEquals(RepeatRule.NONE, RepeatRule.from("every-blue-moon"))
        assertEquals(RepeatRule.WEEKLY, RepeatRule.from("weekly"))
    }
}
