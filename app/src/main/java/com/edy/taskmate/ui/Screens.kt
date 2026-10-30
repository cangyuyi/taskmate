package com.edy.taskmate.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edy.taskmate.SyncState
import com.edy.taskmate.TaskViewModel
import com.edy.taskmate.alarm.Notifications
import com.edy.taskmate.alarm.ReminderScheduler
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.Grouping
import com.edy.taskmate.data.SectionTint
import com.edy.taskmate.data.Task
import com.edy.taskmate.data.TaskFilter
import com.edy.taskmate.data.taskComparator

@Composable
fun LargeTitle(title: String, onAdd: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(subtleFill())
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加任务", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ 今天

@Composable
fun TodayScreen(vm: TaskViewModel, onAdd: () -> Unit, onEdit: (Task) -> Unit) {
    val all = vm.tasks
    val buckets = remember(all) { Grouping.today(all) }
    val unscheduled = remember(all) { all.filter { !it.done && it.dueAt == null }.sortedWith(taskComparator) }
    var expanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
    ) {
        item {
            Column {
                LargeTitle("今天", onAdd)
                if (vm.syncEnabled) {
                    SyncBanner(vm)
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.padding(horizontal = 16.dp)) { HeroCard(buckets, onAdd) }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (buckets.overdue.isNotEmpty()) {
            item { SectionHeader("已逾期", SectionTint.RED, buckets.overdue.size, "尽快处理") }
            item { TaskGroup(buckets.overdue, vm, onEdit) }
        }

        if (buckets.today.isNotEmpty()) {
            item { SectionHeader("今天", SectionTint.ACCENT, buckets.today.size) }
            item { TaskGroup(buckets.today, vm, onEdit) }
        }

        if (unscheduled.isNotEmpty()) {
            item {
                SectionHeader("待安排", SectionTint.NONE, unscheduled.size, "点一下定时间")
            }
            item { TaskGroup(unscheduled, vm, onEdit) }
        }

        if (buckets.overdue.isEmpty() && buckets.today.isEmpty() && unscheduled.isEmpty()) {
            item {
                Column {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        GroupCard {
                            EmptyState(
                                title = if (buckets.completedToday.isEmpty()) "今天没有待办" else "今天的任务都完成了",
                                message = if (buckets.completedToday.isEmpty()) "点下面的「添加任务」，或者享受这份清静。" else "干得漂亮，剩下的时间留给自己。",
                                actionTitle = "添加任务",
                                onAction = onAdd
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        if (buckets.completedToday.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已完成", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = secondaryLabel())
                    Spacer(Modifier.width(5.dp))
                    Text("${buckets.completedToday.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tertiaryLabel())
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = tertiaryLabel(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (expanded) {
                item { TaskGroup(buckets.completedToday, vm, onEdit) }
            }
        }

        item {
            Column {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GroupCard { AddTaskRow(onClick = onAdd) }
                }
            }
        }
    }
}

@Composable
private fun TaskGroup(tasks: List<Task>, vm: TaskViewModel, onEdit: (Task) -> Unit) {
    Column {
        Box(Modifier.padding(horizontal = 16.dp)) {
            GroupCard {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) RowDivider()
                    TaskRow(
                        task = task,
                        onToggle = { vm.toggleDone(task) },
                        onClick = { onEdit(task) }
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

// ------------------------------------------------------------------ 本周

@Composable
fun WeekScreen(vm: TaskViewModel, onAdd: (Long) -> Unit, onEdit: (Task) -> Unit) {
    var weekOffset by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(Dates.startOfToday()) }

    val weekStart = remember(weekOffset) { Dates.addDays(Dates.startOfWeek(System.currentTimeMillis()), weekOffset * 7) }
    val days = remember(weekStart) { Dates.weekDays(weekStart) }
    val all = vm.tasks
    val buckets = remember(all, weekStart) { Grouping.week(all, weekStart) }
    val stats = remember(all, weekStart) { Grouping.weekStats(all, weekStart) }
    val remaining = remember(buckets) { buckets.associate { it.day to it.tasks.count { task -> !task.done } } }
    val selectedBucket = buckets.firstOrNull { Dates.isSameDay(it.day, selected) }
    val selectedRemaining = selectedBucket?.tasks?.count { !it.done } ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
    ) {
        item {
            Column {
            LargeTitle("本周") { onAdd(selected) }
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard {
                    Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WeekNavButton("‹") {
                                val index = Dates.weekdayIndex(selected)
                                weekOffset -= 1
                                selected = Dates.addDays(Dates.addDays(Dates.startOfWeek(System.currentTimeMillis()), weekOffset * 7), index)
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Dates.monthTitle(weekStart), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${Dates.monthDayText(weekStart)} – ${Dates.monthDayText(days.last())}",
                                    fontSize = 12.sp,
                                    color = secondaryLabel()
                                )
                            }
                            WeekNavButton("›") {
                                val index = Dates.weekdayIndex(selected)
                                weekOffset += 1
                                selected = Dates.addDays(Dates.addDays(Dates.startOfWeek(System.currentTimeMillis()), weekOffset * 7), index)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        WeekStrip(days, selected, remaining) { selected = it }

                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.padding(horizontal = 18.dp)) {
                            Row {
                                Text("本周进度", fontSize = 12.sp, color = secondaryLabel())
                                Spacer(Modifier.weight(1f))
                                Text("${stats.second}/${stats.first}", fontSize = 12.sp, color = secondaryLabel())
                            }
                            Spacer(Modifier.height(7.dp))
                            ThinProgressBar(if (stats.first == 0) 0f else stats.second.toFloat() / stats.first.toFloat())
                        }

                        if (weekOffset != 0) {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "回到本周",
                                    color = accentColor(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            weekOffset = 0
                                            selected = Dates.startOfToday()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            }
        }

        item {
            SectionHeader(
                title = Dates.relativeDayText(selected),
                tint = if (Dates.isToday(selected)) SectionTint.ACCENT else SectionTint.NONE,
                trailing = if (selectedRemaining == 0) "无待办" else "$selectedRemaining 项待办"
            )
        }

        item {
            if (selectedBucket == null || selectedBucket.tasks.isEmpty()) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GroupCard {
                        EmptyState(
                            title = "这一天还是空的",
                            message = "给 ${Dates.relativeDayText(selected)} 安排一件小事吧。",
                            actionTitle = "添加任务",
                            onAction = { onAdd(selected) }
                        )
                    }
                }
            } else {
                TaskGroup(selectedBucket.tasks, vm, onEdit)
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard { AddTaskRow("在${Dates.relativeDayText(selected)}添加任务") { onAdd(selected) } }
            }
        }
    }
}

@Composable
private fun WeekNavButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(subtleFill())
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ------------------------------------------------------------------ 全部

@Composable
fun AllScreen(vm: TaskViewModel, onAdd: () -> Unit, onEdit: (Task) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ACTIVE) }

    val all = vm.tasks
    val searching = query.trim().isNotEmpty()
    val results = remember(all, query) { Grouping.search(all, query) }
    val sections = remember(all, filter) { Grouping.all(all, filter) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
    ) {
        item {
            Column {
                LargeTitle("全部", onAdd)
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SearchField(query) { query = it }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskFilter.values().forEach { item ->
                        Chip(item.label, filter == item) { filter = item }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (searching) {
            if (results.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        GroupCard { EmptyState(title = "没有找到", message = "换个关键词试试。") }
                    }
                }
            } else {
                item { SectionHeader("搜索结果", SectionTint.NONE, results.size) }
                item { TaskGroup(results, vm, onEdit) }
            }
        } else if (sections.isEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GroupCard {
                        EmptyState(
                            title = if (filter == TaskFilter.DONE) "还没有完成的任务" else "还没有任务",
                            message = if (filter == TaskFilter.DONE) "完成的任务会出现在这里。" else "把想到的事情写下来，一件一件处理。",
                            actionTitle = "添加任务",
                            onAction = onAdd
                        )
                    }
                }
            }
        } else {
            sections.forEach { section ->
                item(key = "h-${section.id}") {
                    SectionHeader(section.title, section.tint, section.tasks.size)
                }
                item(key = "g-${section.id}") {
                    TaskGroup(section.tasks, vm, onEdit)
                }
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard { AddTaskRow(onClick = onAdd) }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(subtleFill())
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = secondaryLabel(), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("搜索任务", color = secondaryLabel(), fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ------------------------------------------------------------------ 设置

@Composable
fun SettingsScreen(vm: TaskViewModel, onAbout: () -> Unit = {}) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(vm.serverUrl) }
    var key by remember { mutableStateOf(vm.apiKey) }
    var enabled by remember { mutableStateOf(vm.syncEnabled) }
    val notificationAllowed = Notifications.canPost(context)
    val stats = remember(vm.tasks) { Grouping.stats(vm.tasks) }
    val scheduledCount = vm.tasks.count { it.canRemind }

    var exportPayload by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = exportPayload
        exportPayload = null
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
    ) {
        item {
            LargeTitle("设置")
        }

        item {
            SectionHeader("提醒")
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard {
                    SettingsRow(
                        title = "通知权限",
                        value = if (notificationAllowed) "已开启" else "未开启",
                        valueColor = if (notificationAllowed) greenColor() else redColor()
                    )
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(title = "已安排提醒", value = "$scheduledCount 个")
                    if (!notificationAllowed) {
                        RowDivider(startIndent = 16.dp)
                        SettingsRow(
                            title = "去开启通知",
                            value = "打开系统设置",
                            valueColor = accentColor(),
                            onClick = { openNotificationSettings(context) }
                        )
                    }
                    RowDivider(startIndent = 16.dp)
                    val exactAllowed = ReminderScheduler.canScheduleExact(context)
                    SettingsRow(
                        title = "精确提醒",
                        value = if (exactAllowed) "已允许" else "未允许，可能延迟",
                        valueColor = if (exactAllowed) greenColor() else orangeColor(),
                        onClick = { if (!exactAllowed) openExactAlarmSettings(context) }
                    )
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(
                        title = "发送测试通知",
                        value = "立即",
                        valueColor = accentColor(),
                        onClick = { vm.postTestNotification() }
                    )
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(
                        title = "60 秒后测试提醒",
                        value = "验证闹钟",
                        valueColor = accentColor(),
                        onClick = { vm.scheduleTestReminder() }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "小米手机建议：设置 → 应用设置 → 任务助理 → 自启动打开、省电策略选「无限制」，否则系统可能延迟提醒。",
                fontSize = 12.sp,
                color = secondaryLabel(),
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionHeader("电脑端同步")
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("服务器地址", fontSize = 13.sp, color = secondaryLabel())
                        Spacer(Modifier.height(6.dp))
                        SyncField(url, "http://192.168.1.10:8788") { url = it }

                        Spacer(Modifier.height(12.dp))
                        Text("API Key", fontSize = 13.sp, color = secondaryLabel())
                        Spacer(Modifier.height(6.dp))
                        SyncField(key, "服务端启动日志里的 key") { key = it }

                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("启用同步", fontSize = 16.sp)
                                Text(syncStateText(vm.syncState), fontSize = 12.sp, color = syncStateColor(vm.syncState))
                            }
                            androidx.compose.material3.Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    vm.saveSyncSettings(url, key, it)
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor())
                                    .clickable {
                                        vm.saveSyncSettings(url, key, true)
                                        enabled = true
                                        vm.syncNow()
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("保存并同步", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(subtleFill())
                                    .clickable { vm.syncNow() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("立即拉取", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "电脑和手机连同一个 Wi-Fi，电脑上运行 server（见 README），手机填电脑的局域网 IP。没开同步也能用，只是任务不互通。",
                fontSize = 12.sp,
                color = secondaryLabel(),
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionHeader("数据")
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard {
                    SettingsRow(title = "任务总数", value = "${stats.total}")
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(title = "已完成", value = "${stats.done}")
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(
                        title = "导出备份",
                        value = "JSON 文件",
                        valueColor = accentColor(),
                        onClick = {
                            exportPayload = vm.exportJson()
                            exportLauncher.launch("taskmate-backup.json")
                        }
                    )
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(title = "清空已完成", value = "不可撤销", valueColor = redColor()) {
                        vm.clearCompleted()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionHeader("关于")
            Box(Modifier.padding(horizontal = 16.dp)) {
                GroupCard {
                    SettingsRow(title = "版本", value = "1.0")
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(title = "数据存储", value = "本机 + 你的电脑")
                    RowDivider(startIndent = 16.dp)
                    SettingsRow(title = "设计", value = "iOS 原生风格")
                }
            }
        }
    }
}

@Composable
private fun SyncField(value: String, placeholder: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(subtleFill())
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = tertiaryLabel(), fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun syncStateText(state: SyncState): String = when (state) {
    is SyncState.Idle -> "未开启"
    is SyncState.Syncing -> "同步中…"
    is SyncState.Success -> "已同步 " + Dates.timeText(state.at)
    is SyncState.Failure -> state.message
}

@Composable
private fun syncStateColor(state: SyncState): androidx.compose.ui.graphics.Color = when (state) {
    is SyncState.Success -> greenColor()
    is SyncState.Failure -> redColor()
    is SyncState.Syncing -> orangeColor()
    is SyncState.Idle -> secondaryLabel()
}
private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openExactAlarmSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 今天页顶部的同步状态条：失败时能一眼看见，点一下重试。 */
@Composable
private fun SyncBanner(vm: TaskViewModel) {
    val state = vm.syncState
    val text: String
    val color: androidx.compose.ui.graphics.Color
    when (state) {
        is SyncState.Syncing -> {
            text = "正在与电脑同步…"
            color = secondaryLabel()
        }
        is SyncState.Success -> {
            text = "已同步 " + Dates.timeText(state.at)
            color = greenColor()
        }
        is SyncState.Failure -> {
            text = state.message
            color = redColor()
        }
        SyncState.Idle -> {
            text = "同步待启动"
            color = secondaryLabel()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(subtleFill())
            .clickable { vm.syncNow() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("同步", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accentColor())
    }
}
