package com.edy.taskmate

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edy.taskmate.alarm.Notifications
import com.edy.taskmate.alarm.ReminderScheduler
import com.edy.taskmate.alarm.TaskWidget
import com.edy.taskmate.data.AppSettings
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.SyncClient
import com.edy.taskmate.data.Task
import com.edy.taskmate.data.TaskOps
import com.edy.taskmate.data.TaskStore
import com.edy.taskmate.data.taskComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val at: Long) : SyncState()
    data class Failure(val message: String) : SyncState()
}

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val store = TaskStore(appContext)
    private val settings = AppSettings(appContext)
    private val client = SyncClient()
    private var debounceJob: Job? = null
    private val syncMutex = Mutex()

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set
    var serverUrl by mutableStateOf(settings.serverUrl)
        private set
    var apiKey by mutableStateOf(settings.apiKey)
        private set
    var syncEnabled by mutableStateOf(settings.syncEnabled)
        private set
    var syncState by mutableStateOf<SyncState>(SyncState.Idle)
        private set
    var lastSyncAt by mutableStateOf(settings.lastSyncAt)
        private set
    var pendingOpenTaskId by mutableStateOf<String?>(null)
        private set
    var lastDeleted by mutableStateOf<Task?>(null)
        private set

    init {
        val all = store.load()
        tasks = all.filter { !it.deleted }.sortedWith(taskComparator)
        ReminderScheduler.syncAll(appContext, all)
        TaskWidget.update(appContext)
        if (settings.syncEnabled) syncNow()
    }

    // ---------------------------------------------------------------- 增删改

    fun addTask(
        title: String,
        notes: String = "",
        dueAt: Long? = null,
        remindAt: Long? = null,
        priority: Int = Task.PRIORITY_NORMAL,
        repeat: String = ""
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val task = Task.create(
            title = trimmed,
            notes = notes.trim(),
            dueAt = dueAt,
            remindAt = remindAt,
            priority = priority,
            repeatRule = repeat
        )
        viewModelScope.launch {
            persist(withContext(Dispatchers.IO) { store.load() } + task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.load() }
            persist(TaskOps.edit(all, task))
        }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.load() }
            persist(TaskOps.toggle(all, task.id))
        }
    }

    fun deleteTask(task: Task) {
        lastDeleted = task
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.load() }
            persist(TaskOps.delete(all, task.id))
        }
    }

    fun undoDelete() {
        val task = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.load() }
            persist(TaskOps.restore(all, task))
        }
    }

    fun dismissUndo() {
        lastDeleted = null
    }

    fun clearCompleted() {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.load() }
            persist(all.map { if (it.done) it.copy(deleted = true).touched() else it })
        }
    }

    /** 顺延到明天同一时刻（没有时刻就 9:00），提醒时间一起平移。 */
    fun postponeToTomorrow(task: Task) {
        val reference = task.dueAt ?: System.currentTimeMillis()
        val hour = if (task.dueAt == null) 9 else Dates.hourOf(reference)
        val minute = if (task.dueAt == null) 0 else {
            val start = Dates.startOfDay(reference)
            ((reference - start) / 60000L % 60L).toInt()
        }
        val tomorrow = Dates.addDays(Dates.startOfToday(), 1)
        var newDue = Dates.atTime(tomorrow, hour, minute)
        if (newDue <= System.currentTimeMillis()) newDue = Dates.addDays(newDue, 1)

        val offset = if (task.dueAt != null && task.remindAt != null) task.remindAt!! - task.dueAt!! else null
        val newRemind = offset?.let { newDue + it }

        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { store.load() }
            persist(
                all.map {
                    if (it.id == task.id) it.copy(dueAt = newDue, remindAt = newRemind).touched() else it
                }
            )
        }
    }

    private suspend fun persist(all: List<Task>) {
        withContext(Dispatchers.IO) { store.save(all) }
        tasks = all.filter { !it.deleted }.sortedWith(taskComparator)
        ReminderScheduler.syncAll(appContext, all)
        TaskWidget.update(appContext)
        scheduleSync()
    }

    // ---------------------------------------------------------------- 提醒自检

    fun postTestNotification() {
        Notifications.ensureChannel(appContext)
        Notifications.postTest(appContext)
    }

    fun scheduleTestReminder() {
        ReminderScheduler.scheduleTest(appContext, 60)
    }

    // ---------------------------------------------------------------- 备份

    /** 导出为 JSON 文本，交给系统文件选择器保存。 */
    fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "TaskMate")
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        root.put("tasks", array)
        return root.toString(2)
    }

    // ---------------------------------------------------------------- 同步

    fun saveSyncSettings(url: String, key: String, enabled: Boolean) {
        val cleaned = url.trim().let { if (it.endsWith("/")) it.dropLast(1) else it }
        serverUrl = cleaned
        apiKey = key.trim()
        syncEnabled = enabled
        settings.serverUrl = cleaned
        settings.apiKey = apiKey
        settings.syncEnabled = enabled
        if (enabled) syncNow() else syncState = SyncState.Idle
    }

    fun syncNow() {
        if (!syncEnabled) {
            syncState = SyncState.Idle
            return
        }
        if (serverUrl.isBlank() || apiKey.isBlank()) {
            syncState = SyncState.Failure("请先填写服务器地址和 API Key")
            return
        }
        viewModelScope.launch { doSync() }
    }

    fun onResume() {
        if (!syncEnabled) return
        if (System.currentTimeMillis() - lastSyncAt > 15_000L) {
            syncNow()
        }
    }

    private fun scheduleSync(delayMs: Long = 900L) {
        if (!syncEnabled) return
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(delayMs)
            doSync()
        }
    }

    private suspend fun doSync() = syncMutex.withLock {
        // 刚同步过就不重复拉（启动 + 前台恢复会同时触发）
        if (System.currentTimeMillis() - lastSyncAt in 1..2_000L && syncState is SyncState.Success) return@withLock

        syncState = SyncState.Syncing
        try {
            val result = withContext(Dispatchers.IO) {
                val all = store.load()
                val response = client.sync(serverUrl, apiKey, settings.deviceId, settings.lastSyncAt, all)

                val merged = all.associateBy { it.id }.toMutableMap()
                response.tasks.forEach { incoming ->
                    val existing = merged[incoming.id]
                    if (existing == null || incoming.updatedAt > existing.updatedAt) {
                        merged[incoming.id] = incoming
                    }
                }
                val list = merged.values.toList()
                store.save(list)
                list to response.now
            }

            val merged = result.first
            val now = result.second
            settings.lastSyncAt = now
            lastSyncAt = now
            tasks = merged.filter { !it.deleted }.sortedWith(taskComparator)
            ReminderScheduler.syncAll(appContext, merged)
            TaskWidget.update(appContext)
            syncState = SyncState.Success(now)
        } catch (error: CancellationException) {
            // 协程被取消不是错误，不要污染界面
            throw error
        } catch (error: Exception) {
            syncState = SyncState.Failure(friendlySyncError(error))
        }
    }

    private fun friendlySyncError(error: Exception): String = when (error) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException ->
            "连不上电脑：检查同一 Wi-Fi、地址和端口"
        is java.net.SocketTimeoutException -> "连接超时：电脑端服务在跑吗？"
        else -> error.message ?: "同步失败"
    }

    // ---------------------------------------------------------------- 通知跳转

    fun handleOpenTask(taskId: String?) {
        if (!taskId.isNullOrBlank() && tasks.any { it.id == taskId }) {
            pendingOpenTaskId = taskId
        }
    }

    fun consumePendingOpenTask() {
        pendingOpenTaskId = null
    }
}