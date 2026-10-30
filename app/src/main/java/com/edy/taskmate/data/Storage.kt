package com.edy.taskmate.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * 本地持久化：一个 JSON 文件，简单、可读、方便备份。
 */
class TaskStore(context: Context) {

    private val dir: File = context.filesDir
    private val file = File(dir, "tasks.json")

    fun load(): List<Task> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { Task.fromJson(array.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    fun save(tasks: List<Task>) {
        val array = JSONArray()
        tasks.forEach { array.put(it.toJson()) }
        val tmp = File(dir, "tasks.json.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString())
            tmp.delete()
        }
    }
}

/**
 * 同步设置（服务器地址、API Key、开关）与设备标识。
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("taskmate", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString("server_url", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", false)
        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(value) = prefs.edit().putLong("last_sync_at", value).apply()

    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (existing != null) return existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", created).apply()
            return created
        }
}