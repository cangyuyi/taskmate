package com.edy.taskmate.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 所有日期计算与中文文案。一周从周一开始。
 */
object Dates {

    private val weekdayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")

    private fun cal(ts: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance(Locale.CHINA).apply { timeInMillis = ts }

    fun startOfDay(ts: Long): Long = cal(ts).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

    fun addDays(ts: Long, days: Int): Long = cal(ts).apply {
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    /** 含 ts 的那一周的周一 00:00。 */
    fun startOfWeek(ts: Long): Long {
        val c = cal(startOfDay(ts))
        val dayOfWeek = c.get(Calendar.DAY_OF_WEEK) // 周日=1 … 周六=7
        val delta = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
        c.add(Calendar.DAY_OF_YEAR, delta)
        return c.timeInMillis
    }

    fun weekDays(weekStart: Long): List<Long> = (0 until 7).map { addDays(weekStart, it) }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = cal(a)
        val cb = cal(b)
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun isToday(ts: Long): Boolean = isSameDay(ts, System.currentTimeMillis())

    fun isTomorrow(ts: Long): Boolean = isSameDay(ts, addDays(System.currentTimeMillis(), 1))

    /** 周一 = 0 … 周日 = 6 */
    fun weekdayIndex(ts: Long): Int = (cal(ts).get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun weekdayShort(ts: Long): String = weekdayNames[weekdayIndex(ts)]

    fun weekdayName(ts: Long): String = "周" + weekdayShort(ts)

    fun hourOf(ts: Long): Int = cal(ts).get(Calendar.HOUR_OF_DAY)

    fun timeText(ts: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))

    fun monthDayText(ts: Long): String = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(ts))

    fun monthTitle(ts: Long): String = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(ts))

    fun dayNumberText(ts: Long): String = cal(ts).get(Calendar.DAY_OF_MONTH).toString()

    fun fullText(ts: Long): String =
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(ts)) + " " + weekdayName(ts)

    fun relativeDayText(ts: Long): String = when {
        isToday(ts) -> "今天"
        isTomorrow(ts) -> "明天"
        isSameDay(ts, addDays(System.currentTimeMillis(), -1)) -> "昨天"
        else -> monthDayText(ts) + " " + weekdayName(ts)
    }

    fun greeting(): String = when (hourOf(System.currentTimeMillis())) {
        in 0..5 -> "夜深了"
        in 6..8 -> "早上好"
        in 9..11 -> "上午好"
        in 12..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }

    /** 把时间设到某天的某个时刻。 */
    fun atTime(dayTs: Long, hour: Int, minute: Int): Long = cal(startOfDay(dayTs)).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 严格晚于现在的下一个 5 分钟整点。 */
    fun nextRound(): Long {
        val now = System.currentTimeMillis()
        val c = cal(now)
        val minute = c.get(Calendar.MINUTE)
        c.add(Calendar.MINUTE, 5 - minute % 5)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * 快速添加的默认到期时间：1 小时后，向上取整到 5 分钟。
     * 比“下个整点”更符合直觉，也不会一打开就提示时间已过去。
     */
    fun defaultDue(): Long {
        val now = System.currentTimeMillis()
        val c = cal(now)
        c.add(Calendar.HOUR_OF_DAY, 1)
        val minute = c.get(Calendar.MINUTE)
        c.add(Calendar.MINUTE, 5 - minute % 5)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val candidate = c.timeInMillis
        // 别跨天：23:40 + 1h 就到明天了，宁可留在今晚
        return if (!isSameDay(candidate, now)) atTime(now, 23, 55) else candidate
    }

    fun nextMonday(): Long {
        val today = startOfToday()
        val index = weekdayIndex(today)
        val delta = if (index == 0) 7 else 7 - index
        return addDays(today, delta)
    }
}