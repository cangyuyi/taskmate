package com.edy.taskmate

import com.edy.taskmate.data.RepeatRule
import com.edy.taskmate.data.Task
import com.edy.taskmate.data.TaskOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskOpsTest {

    private fun task(
        id: String = "t1",
        title: String = "晨跑",
        dueAt: Long? = null,
        remindAt: Long? = null,
        repeat: String = ""
    ) = Task(
        id = id,
        title = title,
        dueAt = dueAt,
        remindAt = remindAt,
        repeatRule = repeat,
        updatedAt = 1L,
        createdAt = 1L
    )

    private val tomorrow9: Long = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun completingNormalTaskDoesNotSpawn() {
        val list = listOf(task(dueAt = tomorrow9, remindAt = tomorrow9 - 600_000))
        val result = TaskOps.complete(list, "t1")
        assertEquals(1, result.size)
        assertTrue(result[0].done)
    }

    @Test
    fun completingRepeatingTaskSpawnsNext() {
        val list = listOf(task(dueAt = tomorrow9, remindAt = tomorrow9 - 600_000, repeat = "daily"))
        val result = TaskOps.complete(list, "t1")

        assertEquals(2, result.size)
        val done = result.first { it.id == "t1" }
        val next = result.first { it.id != "t1" }

        assertTrue(done.done)
        assertEquals(RepeatRule.DAILY.id, next.repeatRule)
        assertTrue(next.dueAt!! > System.currentTimeMillis())
        assertNotNull(next.remindAt)
        // 提前量保持不变
        assertEquals(600_000L, next.dueAt!! - next.remindAt!!)
    }

    @Test
    fun completingTwiceIsIdempotent() {
        val list = listOf(task(dueAt = tomorrow9, repeat = "daily"))
        val once = TaskOps.complete(list, "t1")
        val twice = TaskOps.complete(once, "t1")
        assertEquals(once.size, twice.size)
    }

    @Test
    fun deleteThenRestoreBringsItBack() {
        val original = task(dueAt = tomorrow9)
        val deleted = TaskOps.delete(listOf(original), "t1")
        assertTrue(deleted[0].deleted)

        val restored = TaskOps.restore(deleted, original)
        assertTrue(!restored[0].deleted)
    }

    @Test
    fun snoozeOnlyTouchesReminder() {
        val due = tomorrow9
        val list = listOf(task(dueAt = due, remindAt = due - 600_000))
        val until = System.currentTimeMillis() + 600_000
        val result = TaskOps.snooze(list, "t1", until)

        assertEquals(due, result[0].dueAt)
        assertEquals(until, result[0].remindAt)
    }

    @Test
    fun toggleFlipsBothWays() {
        val list = listOf(task())
        val done = TaskOps.toggle(list, "t1")
        assertTrue(done[0].done)
        val undone = TaskOps.toggle(done, "t1")
        assertTrue(!undone[0].done)
        assertNull(done.firstOrNull { it.id == "missing" })
    }
}
