package com.edy.taskmate.data

/**
 * 纯函数形式的任务操作，确保「App 里点完成」和「通知栏点完成」走同一套逻辑。
 */
object TaskOps {

    fun edit(all: List<Task>, updated: Task): List<Task> =
        all.map { if (it.id == updated.id) updated.touched() else it }

    /**
     * 完成一条任务。如果是重复任务，顺手生成下一次。
     */
    fun complete(all: List<Task>, taskId: String, now: Long = System.currentTimeMillis()): List<Task> {
        val task = all.firstOrNull { it.id == taskId } ?: return all
        if (task.done) return all

        val result = all.map { if (it.id == taskId) task.copy(done = true).touched() else it }.toMutableList()

        val rule = task.repeat
        val due = task.dueAt
        if (rule != RepeatRule.NONE && due != null) {
            val nextDue = Repeat.nextFutureTime(due, rule, now)
            val offset = task.remindAt?.let { it - due }
            result.add(
                Task.create(
                    title = task.title,
                    notes = task.notes,
                    dueAt = nextDue,
                    remindAt = offset?.let { nextDue + it },
                    priority = task.priority,
                    repeatRule = rule.id
                )
            )
        }
        return result
    }

    fun uncomplete(all: List<Task>, taskId: String): List<Task> =
        all.map { if (it.id == taskId) it.copy(done = false).touched() else it }

    fun toggle(all: List<Task>, taskId: String): List<Task> {
        val task = all.firstOrNull { it.id == taskId } ?: return all
        return if (task.done) uncomplete(all, taskId) else complete(all, taskId)
    }

    fun delete(all: List<Task>, taskId: String): List<Task> =
        all.map { if (it.id == taskId) it.copy(deleted = true).touched() else it }

    fun restore(all: List<Task>, task: Task): List<Task> {
        val exists = all.any { it.id == task.id }
        return if (exists) {
            all.map { if (it.id == task.id) it.copy(deleted = false).touched() else it }
        } else {
            all + task.copy(deleted = false).touched()
        }
    }

    /** 稍后提醒：只改提醒时间，不动到期时间。 */
    fun snooze(all: List<Task>, taskId: String, until: Long): List<Task> =
        all.map { if (it.id == taskId) it.copy(remindAt = until).touched() else it }
}