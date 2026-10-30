package com.edy.taskmate.data

/** 今日视图的三组任务。 */
data class TodayBuckets(
    val overdue: List<Task>,
    val today: List<Task>,
    val completedToday: List<Task>
) {
    val total: Int get() = overdue.size + today.size + completedToday.size
    val remaining: Int get() = overdue.size + today.size
    val doneCount: Int get() = completedToday.size
    val progress: Float get() = if (total == 0) 0f else doneCount.toFloat() / total.toFloat()
}

data class DayBucket(val day: Long, val tasks: List<Task>)

enum class SectionTint { RED, ACCENT, NONE }

data class TaskSection(
    val id: String,
    val title: String,
    val tint: SectionTint,
    val tasks: List<Task>
)

enum class TaskFilter(val label: String) {
    ACTIVE("待办"),
    DONE("已完成"),
    ALL("全部")
}

data class TaskStats(
    val total: Int,
    val active: Int,
    val done: Int,
    val overdue: Int,
    val today: Int,
    val week: Int
)

object Grouping {

    fun today(all: List<Task>): TodayBuckets {
        val start = Dates.startOfToday()
        val end = Dates.addDays(start, 1)
        val visible = all.filter { !it.deleted }

        val overdue = visible
            .filter { !it.done && it.dueAt != null && it.dueAt < start }
            .sortedWith(taskComparator)

        val today = visible
            .filter { !it.done && it.dueAt != null && it.dueAt >= start && it.dueAt < end }
            .sortedWith(taskComparator)

        val completed = visible
            .filter { it.done && it.updatedAt >= start }
            .sortedByDescending { it.updatedAt }

        return TodayBuckets(overdue = overdue, today = today, completedToday = completed)
    }

    fun week(all: List<Task>, weekStart: Long): List<DayBucket> {
        val visible = all.filter { !it.deleted }
        return Dates.weekDays(weekStart).map { day ->
            val dayTasks = visible
                .filter { it.dueAt != null && Dates.isSameDay(it.dueAt!!, day) }
                .sortedWith(compareBy<Task>({ it.done }, { it.dueAt ?: Long.MAX_VALUE }))
            DayBucket(day = day, tasks = dayTasks)
        }
    }

    fun weekStats(all: List<Task>, weekStart: Long): Pair<Int, Int> {
        val days = Dates.weekDays(weekStart)
        val visible = all.filter { !it.deleted && it.dueAt != null && days.any { d -> Dates.isSameDay(it.dueAt!!, d) } }
        return visible.size to visible.count { it.done }
    }

    fun all(all: List<Task>, filter: TaskFilter): List<TaskSection> {
        val visible = all.filter { !it.deleted }
        val start = Dates.startOfToday()
        val end = Dates.addDays(start, 1)
        val weekEnd = Dates.addDays(Dates.startOfWeek(System.currentTimeMillis()), 7)

        val active = visible.filter { !it.done }.sortedWith(taskComparator)
        val done = visible.filter { it.done }.sortedByDescending { it.updatedAt }

        if (filter == TaskFilter.DONE) {
            return if (done.isEmpty()) emptyList()
            else listOf(TaskSection("done", "已完成", SectionTint.NONE, done))
        }

        val sections = mutableListOf<TaskSection>()

        fun add(id: String, title: String, tint: SectionTint, list: List<Task>) {
            if (list.isNotEmpty()) sections.add(TaskSection(id, title, tint, list))
        }

        add("overdue", "已逾期", SectionTint.RED, active.filter { it.dueAt != null && it.dueAt < start })
        add("today", "今天", SectionTint.ACCENT, active.filter { it.dueAt != null && it.dueAt >= start && it.dueAt < end })
        add("week", "本周稍后", SectionTint.NONE, active.filter { it.dueAt != null && it.dueAt >= end && it.dueAt < weekEnd })
        add("later", "以后", SectionTint.NONE, active.filter { it.dueAt != null && it.dueAt >= weekEnd })
        add("someday", "没有日期", SectionTint.NONE, active.filter { it.dueAt == null })
        if (filter == TaskFilter.ALL) add("done", "已完成", SectionTint.NONE, done)

        return sections
    }

    fun search(all: List<Task>, query: String): List<Task> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { !it.deleted && (it.title.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true)) }
            .sortedWith(taskComparator)
    }

    fun stats(all: List<Task>): TaskStats {
        val visible = all.filter { !it.deleted }
        val start = Dates.startOfToday()
        val end = Dates.addDays(start, 1)
        val weekEnd = Dates.addDays(Dates.startOfWeek(System.currentTimeMillis()), 7)

        return TaskStats(
            total = visible.size,
            active = visible.count { !it.done },
            done = visible.count { it.done },
            overdue = visible.count { !it.done && it.dueAt != null && it.dueAt < start },
            today = visible.count { !it.done && it.dueAt != null && it.dueAt >= start && it.dueAt < end },
            week = visible.count { !it.done && it.dueAt != null && it.dueAt >= start && it.dueAt < weekEnd }
        )
    }
}