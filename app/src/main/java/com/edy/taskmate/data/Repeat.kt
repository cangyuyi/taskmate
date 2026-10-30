package com.edy.taskmate.data

import java.util.Calendar

/**
 * 重复规则。值要跟服务端 / 控制台保持一致：空串 = 不重复。
 */
enum class RepeatRule(val id: String, val label: String) {
    NONE("", "不重复"),
    DAILY("daily", "每天"),
    WEEKDAYS("weekdays", "工作日"),
    WEEKLY("weekly", "每周"),
    MONTHLY("monthly", "每月");

    companion object {
        fun from(raw: String?): RepeatRule =
            values().firstOrNull { it.id == (raw ?: "") } ?: NONE

        /** 界面上按这个顺序展示 */
        val picker: List<RepeatRule> = listOf(NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY)
    }
}

object Repeat {

    /**
     * 按规则把时间往后推一次（保留时分）。
     * 月末会做夹取：1 月 31 日的「每月」→ 2 月 28/29 日。
     */
    fun next(after: Long, rule: RepeatRule): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = after }
        when (rule) {
            RepeatRule.NONE -> return after
            RepeatRule.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            RepeatRule.WEEKDAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            RepeatRule.WEEKLY -> calendar.add(Calendar.DAY_OF_YEAR, 7)
            RepeatRule.MONTHLY -> calendar.add(Calendar.MONTH, 1)
        }
        return calendar.timeInMillis
    }

    /**
     * 完成一条重复任务时，算出下一次的时间。
     * 如果下一次还在过去（补做旧任务），一直往前推，避免生成一堆逾期任务。
     */
    fun nextFutureTime(from: Long, rule: RepeatRule, now: Long = System.currentTimeMillis()): Long {
        if (rule == RepeatRule.NONE) return from
        var candidate = next(from, rule)
        var guard = 0
        while (candidate <= now && guard < 400) {
            candidate = next(candidate, rule)
            guard++
        }
        return candidate
    }
}