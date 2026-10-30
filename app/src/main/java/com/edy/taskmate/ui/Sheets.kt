package com.edy.taskmate.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.RepeatRule
import com.edy.taskmate.data.Task
import java.util.Calendar
import kotlinx.coroutines.delay

/** 只选了“哪一天”时会传 00:00，这里补成一个合理时刻，避免默认 0 点。 */
private fun initialMoment(raw: Long?): Long? {
    if (raw == null) return null
    val calendar = Calendar.getInstance().apply { timeInMillis = raw }
    if (calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0) {
        val nine = Dates.atTime(raw, 9, 0)
        return if (nine > System.currentTimeMillis()) nine else Dates.defaultDue()
    }
    return raw
}

private val reminderOffsets = listOf(0 to "准时", 5 to "提前 5 分钟", 15 to "提前 15 分钟", 30 to "提前 30 分钟", 60 to "提前 1 小时")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    initialDate: Long?,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, dueAt: Long?, remindAt: Long?, priority: Int, repeat: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var hasDate by remember { mutableStateOf(initialDate != null) }
    var date by remember { mutableStateOf(initialMoment(initialDate) ?: Dates.defaultDue()) }
    var hasReminder by remember { mutableStateOf(initialDate != null) }
    var offsetMinutes by remember { mutableStateOf(0) }
    var priority by remember { mutableStateOf(Task.PRIORITY_NORMAL) }
    var repeat by remember { mutableStateOf(RepeatRule.NONE) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(260)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    fun reminderDate(): Long? {
        if (!hasDate || !hasReminder) return null
        val candidate = date - offsetMinutes * 60_000L
        return if (candidate > System.currentTimeMillis()) candidate else if (date > System.currentTimeMillis()) date else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("新任务", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = secondaryLabel(),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "添加",
                    color = if (title.isBlank()) tertiaryLabel() else accentColor(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = title.isNotBlank()) {
                            onSave(title.trim(), notes.trim(), if (hasDate) date else null, reminderDate(), priority, repeat.id)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("要做什么？", color = tertiaryLabel()) },
                textStyle = MaterialTheme.typography.titleMedium,
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), notes.trim(), if (hasDate) date else null, reminderDate(), priority, repeat.id)
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cardColor(),
                    unfocusedContainerColor = cardColor(),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(16.dp))

            SectionLabel("时间")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Chip("无日期", !hasDate) { hasDate = false }
                Chip("今天", hasDate && Dates.isToday(date)) { hasDate = true; date = keepClock(date, 0) }
                Chip("明天", hasDate && Dates.isTomorrow(date)) { hasDate = true; date = keepClock(date, 1) }
                Chip("后天", hasDate && Dates.isSameDay(date, Dates.addDays(Dates.startOfToday(), 2))) { hasDate = true; date = keepClock(date, 2) }
                Chip("下周一", hasDate && Dates.isSameDay(date, Dates.nextMonday())) {
                    hasDate = true
                    date = Dates.atTime(Dates.nextMonday(), 9, 0)
                }
            }

            Spacer(Modifier.height(12.dp))
            GroupCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!hasDate) {
                                hasDate = true
                                date = Dates.defaultDue()
                            }
                            showDatePicker = true
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日期", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (hasDate) Dates.relativeDayText(date) else "未设置",
                        color = if (hasDate) secondaryLabel() else accentColor(),
                        fontSize = 15.sp
                    )
                }
                RowDivider(startIndent = 16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!hasDate) {
                                hasDate = true
                                date = Dates.defaultDue()
                            }
                            showTimePicker = true
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("时间", fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (hasDate) Dates.timeText(date) else "点这里选时刻",
                        color = if (hasDate) secondaryLabel() else accentColor(),
                        fontSize = 15.sp
                    )
                }
            }
            if (!hasDate) {
                Text(
                    "不设日期也行，任务会进「今天」页的「待安排」。",
                    fontSize = 12.sp,
                    color = secondaryLabel(),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }

            if (hasDate) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("提醒")
                GroupCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("到点提醒", fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = hasReminder, onCheckedChange = { hasReminder = it })
                    }
                    if (hasReminder) {
                        RowDivider(startIndent = 16.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                reminderOffsets.chunked(3).forEach { group ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        group.forEach { (minutes, label) ->
                                            Chip(label, offsetMinutes == minutes) { offsetMinutes = minutes }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            val at = reminderDate()
                            Text(
                                text = if (at != null) "将在 ${Dates.relativeDayText(at)} ${Dates.timeText(at)} 提醒你" else "这个时间已经过去，不会提醒",
                                fontSize = 12.sp,
                                color = if (at != null) secondaryLabel() else orangeColor()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("优先级")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Task.PRIORITY_LOW, Task.PRIORITY_NORMAL, Task.PRIORITY_HIGH).forEach { value ->
                    Chip(Task.priorityLabel(value), priority == value) { priority = value }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(if (repeat == RepeatRule.NONE) "重复" else "重复 · 完成后自动生成下一次")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepeatRule.picker.forEach { rule ->
                    Chip(rule.label, repeat == rule) { repeat = rule }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("备注（可选）", color = tertiaryLabel()) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cardColor(),
                    unfocusedContainerColor = cardColor(),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (title.isBlank()) tertiaryLabel() else accentColor())
                    .clickable(enabled = title.isNotBlank()) {
                        keyboard?.hide()
                        onSave(title.trim(), notes.trim(), if (hasDate) date else null, reminderDate(), priority, repeat.id)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("添加任务", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDatePicker) {
        ComposeDatePicker(
            initial = date,
            onPicked = { pickedDay ->
                date = combineDate(pickedDay, date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        ComposeTimePicker(
            initial = date,
            onPicked = { hour, minute ->
                date = Dates.atTime(date, hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    task: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onToggleDone: (Task) -> Unit,
    onPostpone: (Task) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes) }
    var hasDate by remember { mutableStateOf(task.dueAt != null) }
    var date by remember { mutableStateOf(task.dueAt ?: Dates.nextRound()) }
    var hasReminder by remember { mutableStateOf(task.remindAt != null) }
    var offsetMinutes by remember {
        mutableStateOf(
            if (task.dueAt != null && task.remindAt != null) {
                ((task.dueAt - task.remindAt) / 60_000L).toInt().coerceAtLeast(0)
            } else 0
        )
    }
    var priority by remember { mutableStateOf(task.priority) }
    var repeat by remember { mutableStateOf(task.repeat) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun reminderDate(): Long? {
        if (!hasDate || !hasReminder) return null
        val candidate = date - offsetMinutes * 60_000L
        return if (candidate > System.currentTimeMillis()) candidate else if (date > System.currentTimeMillis()) date else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("任务详情", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = secondaryLabel(),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "保存",
                    color = if (title.isBlank()) tertiaryLabel() else accentColor(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = title.isNotBlank()) {
                            onSave(
                                task.copy(
                                    title = title.trim(),
                                    notes = notes.trim(),
                                    dueAt = if (hasDate) date else null,
                                    remindAt = reminderDate(),
                                    priority = priority,
                                    repeatRule = repeat.id
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium,
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cardColor(),
                    unfocusedContainerColor = cardColor(),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Chip("无日期", !hasDate) { hasDate = false }
                Chip("今天", hasDate && Dates.isToday(date)) { hasDate = true; date = keepClock(date, 0) }
                Chip("明天", hasDate && Dates.isTomorrow(date)) { hasDate = true; date = keepClock(date, 1) }
                Chip("下周一", hasDate && Dates.isSameDay(date, Dates.nextMonday())) {
                    hasDate = true
                    date = Dates.atTime(Dates.nextMonday(), 9, 0)
                }
            }

            if (hasDate) {
                Spacer(Modifier.height(12.dp))
                GroupCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("日期", fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(Dates.relativeDayText(date), color = secondaryLabel(), fontSize = 15.sp)
                    }
                    RowDivider(startIndent = 16.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("时间", fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(Dates.timeText(date), color = secondaryLabel(), fontSize = 15.sp)
                    }
                    RowDivider(startIndent = 16.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("到点提醒", fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = hasReminder, onCheckedChange = { hasReminder = it })
                    }
                    if (hasReminder) {
                        RowDivider(startIndent = 16.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                reminderOffsets.chunked(3).forEach { group ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        group.forEach { (minutes, label) ->
                                            Chip(label, offsetMinutes == minutes) { offsetMinutes = minutes }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("优先级")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Task.PRIORITY_LOW, Task.PRIORITY_NORMAL, Task.PRIORITY_HIGH).forEach { value ->
                    Chip(Task.priorityLabel(value), priority == value) { priority = value }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel(if (repeat == RepeatRule.NONE) "重复" else "重复 · 完成后自动生成下一次")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepeatRule.picker.forEach { rule ->
                    Chip(rule.label, repeat == rule) { repeat = rule }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("备注（可选）", color = tertiaryLabel()) },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cardColor(),
                    unfocusedContainerColor = cardColor(),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(16.dp))
            GroupCard {
                ActionRow(if (task.done) "标记为未完成" else "标记为已完成", if (task.done) orangeColor() else greenColor()) {
                    onToggleDone(task)
                }
                RowDivider(startIndent = 16.dp)
                ActionRow("顺延到明天", MaterialTheme.colorScheme.onSurface) { onPostpone(task) }
                RowDivider(startIndent = 16.dp)
                ActionRow(if (confirmDelete) "再点一次确认删除" else "删除任务", redColor()) {
                    if (confirmDelete) onDelete(task) else confirmDelete = true
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (title.isBlank()) tertiaryLabel() else accentColor())
                    .clickable(enabled = title.isNotBlank()) {
                        onSave(
                            task.copy(
                                title = title.trim(),
                                notes = notes.trim(),
                                dueAt = if (hasDate) date else null,
                                remindAt = reminderDate(),
                                priority = priority,
                                repeatRule = repeat.id
                            )
                        )
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("保存", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDatePicker) {
        ComposeDatePicker(
            initial = date,
            onPicked = { pickedDay ->
                date = combineDate(pickedDay, date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        ComposeTimePicker(
            initial = date,
            onPicked = { hour, minute ->
                date = Dates.atTime(date, hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun ActionRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, color = color)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = secondaryLabel(),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ComposeDatePicker(initial: Long, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val base = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val hourOfDay = base.get(Calendar.HOUR_OF_DAY)
    val minuteOfHour = base.get(Calendar.MINUTE)

    var year by remember(initial) { mutableStateOf(base.get(Calendar.YEAR)) }
    var month by remember(initial) { mutableStateOf(base.get(Calendar.MONTH)) }
    var selectedDay by remember(initial) { mutableStateOf(base.get(Calendar.DAY_OF_MONTH)) }

    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH)
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

    val firstDay = remember(year, month) {
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
    }
    val leadingBlanks = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一 = 0
    val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7

    fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
            add(Calendar.MONTH, delta)
        }
        year = cal.get(Calendar.YEAR)
        month = cal.get(Calendar.MONTH)
        selectedDay = 1
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StepperButton("‹") { shiftMonth(-1) }
                Text(
                    "${year}年${month + 1}月",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                StepperButton("›") { shiftMonth(1) }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 12.sp,
                            color = secondaryLabel()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                var cell = 0
                while (cell < totalCells) {
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { index ->
                            val dayNumber = cell + index - leadingBlanks + 1
                            val valid = dayNumber in 1..daysInMonth
                            val isSelected = valid && dayNumber == selectedDay
                            val isToday = valid && year == todayYear && month == todayMonth && dayNumber == todayDay

                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(2.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .then(
                                        when {
                                            isSelected -> Modifier.background(accentColor())
                                            isToday -> Modifier.border(1.dp, accentColor(), androidx.compose.foundation.shape.CircleShape)
                                            else -> Modifier
                                        }
                                    )
                                    .clickable(enabled = valid) { selectedDay = dayNumber },
                                contentAlignment = Alignment.Center
                            ) {
                                if (valid) {
                                    Text(
                                        "$dayNumber",
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        cell += 7
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("今天", false) {
                        year = todayYear
                        month = todayMonth
                        selectedDay = todayDay
                    }
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    Chip("明天", false) {
                        year = cal.get(Calendar.YEAR)
                        month = cal.get(Calendar.MONTH)
                        selectedDay = cal.get(Calendar.DAY_OF_MONTH)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(year, month, selectedDay, hourOfDay, minuteOfHour)
                }.timeInMillis
                onPicked(picked)
            }) {
                Text("确定", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ComposeTimePicker(initial: Long, onPicked: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val calendar = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    var hour by remember(initial) { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(initial) { mutableStateOf((calendar.get(Calendar.MINUTE) / 5) * 5) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
        title = { Text("选择时间", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        listOf("现在" to -1, "09:00" to 9, "12:00" to 12),
                        listOf("18:00" to 18, "20:00" to 20)
                    ).forEach { rowPresets ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowPresets.forEach { (label, preset) ->
                                Chip(label, false) {
                                    if (preset < 0) {
                                        val now = Calendar.getInstance()
                                        hour = now.get(Calendar.HOUR_OF_DAY)
                                        val next = ((now.get(Calendar.MINUTE) / 5) + 1) * 5
                                        if (next >= 60) {
                                            minute = 0
                                            hour = (hour + 1) % 24
                                        } else {
                                            minute = next
                                        }
                                    } else {
                                        hour = preset
                                        minute = 0
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberStepper(hour, "时", 0..23, 1) { hour = it }
                    Text(
                        ":",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    NumberStepper(minute, "分", 0..59, 5) { minute = it }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onPicked(hour, minute) }) {
                Text("确定", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NumberStepper(value: Int, label: String, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("−") { onChange(stepValue(value, -step, range)) }
            Text(
                text = value.toString().padStart(2, '0'),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(50.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            StepperButton("+") { onChange(stepValue(value, step, range)) }
        }
        Text(label, fontSize = 12.sp, color = secondaryLabel())
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(subtleFill())
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 在 range 内按 step 前进/后退，越界就回绕。 */
private fun stepValue(value: Int, delta: Int, range: IntRange): Int {
    val span = range.last - range.first + 1
    var next = value + delta
    while (next < range.first) next += span
    while (next > range.last) next -= span
    return next
}

/** 换日期但保留原来的时分。 */
private fun combineDate(pickedDay: Long, timeSource: Long): Long {
    val hour = Calendar.getInstance().apply { timeInMillis = timeSource }.get(Calendar.HOUR_OF_DAY)
    val minute = Calendar.getInstance().apply { timeInMillis = timeSource }.get(Calendar.MINUTE)
    return Dates.atTime(pickedDay, hour, minute)
}

/** 把日期挪到相对今天的第 n 天，保留原来的时分；如果结果已过期就用默认时间。 */
private fun keepClock(current: Long, dayOffset: Int): Long {
    val hour = Calendar.getInstance().apply { timeInMillis = current }.get(Calendar.HOUR_OF_DAY)
    val minute = Calendar.getInstance().apply { timeInMillis = current }.get(Calendar.MINUTE)
    val target = Dates.atTime(Dates.addDays(Dates.startOfToday(), dayOffset), hour, minute)
    return if (target > System.currentTimeMillis()) target else Dates.nextRound()
}