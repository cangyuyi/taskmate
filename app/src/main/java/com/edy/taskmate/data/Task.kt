package com.edy.taskmate.data

import org.json.JSONObject
import java.util.UUID

/**
 * 一条任务。字段与电脑端控制台/同步接口一一对应。
 */
data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val priority: Int = PRIORITY_NORMAL,
    val repeatRule: String = "",
    val done: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
) {
    val isOverdue: Boolean
        get() = !done && dueAt != null && dueAt < System.currentTimeMillis()

    /** 只排「还没到期、还没完成」的提醒。 */
    val canRemind: Boolean
        get() = !done && !deleted && remindAt != null && remindAt > System.currentTimeMillis()

    val repeat: RepeatRule
        get() = RepeatRule.from(repeatRule)

    fun touched(): Task = copy(updatedAt = System.currentTimeMillis())

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("notes", notes)
        put("due_at", dueAt ?: JSONObject.NULL)
        put("remind_at", remindAt ?: JSONObject.NULL)
        put("priority", priority)
        put("repeat_rule", repeatRule)
        put("done", done)
        put("deleted", deleted)
        put("updated_at", updatedAt)
        put("created_at", createdAt)
    }

    companion object {
        const val PRIORITY_LOW = 1
        const val PRIORITY_NORMAL = 2
        const val PRIORITY_HIGH = 3

        fun create(
            title: String,
            notes: String = "",
            dueAt: Long? = null,
            remindAt: Long? = null,
            priority: Int = PRIORITY_NORMAL,
            repeatRule: String = ""
        ): Task {
            val now = System.currentTimeMillis()
            return Task(
                id = UUID.randomUUID().toString(),
                title = title,
                notes = notes,
                dueAt = dueAt,
                remindAt = remindAt,
                priority = priority,
                repeatRule = repeatRule,
                updatedAt = now,
                createdAt = now
            )
        }

        fun fromJson(json: JSONObject): Task = Task(
            id = json.getString("id"),
            title = json.optString("title", ""),
            notes = json.optString("notes", ""),
            dueAt = json.nullableLong("due_at"),
            remindAt = json.nullableLong("remind_at"),
            priority = json.optInt("priority", PRIORITY_NORMAL),
            repeatRule = json.optString("repeat_rule", ""),
            done = json.optBoolean("done", false),
            deleted = json.optBoolean("deleted", false),
            updatedAt = json.optLong("updated_at", 0L),
            createdAt = json.optLong("created_at", 0L)
        )

        fun priorityLabel(priority: Int): String = when (priority) {
            PRIORITY_LOW -> "不急"
            PRIORITY_HIGH -> "重要"
            else -> "普通"
        }
    }
}

private fun JSONObject.nullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key)
}

/** 列表排序：到期早的在前，没有到期时间的排最后；同时间按优先级、创建时间。 */
val taskComparator: Comparator<Task> = compareBy<Task>(
    { it.dueAt ?: Long.MAX_VALUE },
    { -it.priority },
    { it.createdAt }
)