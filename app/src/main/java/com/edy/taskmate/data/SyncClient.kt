package com.edy.taskmate.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class SyncResult(val now: Long, val tasks: List<Task>)

/**
 * 与电脑端控制台服务通信。只用 HttpURLConnection，不引入额外网络库。
 */
class SyncClient {

    fun sync(baseUrl: String, apiKey: String, deviceId: String, since: Long, tasks: List<Task>): SyncResult {
        val payload = JSONObject().apply {
            put("device_id", deviceId)
            put("since", since)
            val array = JSONArray()
            tasks.forEach { array.put(it.toJson()) }
            put("tasks", array)
        }

        val response = postJson(apiUrl(baseUrl, "/api/sync"), apiKey, payload.toString())
        val json = JSONObject(response)
        val array = json.optJSONArray("tasks") ?: JSONArray()
        val list = (0 until array.length()).map { Task.fromJson(array.getJSONObject(it)) }
        return SyncResult(now = json.optLong("now", System.currentTimeMillis()), tasks = list)
    }

    fun ping(baseUrl: String, apiKey: String): Boolean = runCatching {
        val connection = URL(apiUrl(baseUrl, "/healthz")).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val code = connection.responseCode
        connection.disconnect()
        code in 200..299
    }.getOrDefault(false)

    private fun apiUrl(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    private fun postJson(url: String, apiKey: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("X-API-Key", apiKey)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val hint = when (code) {
                    401 -> "API Key 不对"
                    404 -> "服务器地址不对（路径不存在）"
                    else -> text.take(120)
                }
                throw IOException("HTTP $code · $hint")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}